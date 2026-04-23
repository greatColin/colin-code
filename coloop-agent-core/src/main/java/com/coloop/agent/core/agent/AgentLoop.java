package com.coloop.agent.core.agent;

import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.message.MessageBuilder;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Agent 核心循环。单次问答（循环工具调用）
 * <p>
 * 负责 LLM 调用的 while 循环：发送消息 → 接收响应 → 处理 tool_calls → 追加结果 → 继续循环。
 * 支持同步 {@link #chat} 与流式 {@link #chatStream} 两种模式，以及跨轮次消息持久化。
 *
 * @see AgentHook 生命周期钩子
 * @see InputInterceptor 输入拦截器
 */
public class AgentLoop {

    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final MessageBuilder messageBuilder;
    private final List<AgentHook> hooks;
    private final List<InputInterceptor> interceptors;
    private final AppConfig config;

    /** 跨轮次持久化的消息历史 */
    private List<Map<String, Object>> messages;

    /** 运行中待注入的用户消息队列 */
    private final ConcurrentLinkedQueue<String> pendingUserMessages = new ConcurrentLinkedQueue<>();

    /**
     * @param provider        LLM 提供商
     * @param toolRegistry    工具注册表
     * @param messageBuilder  消息构建器
     * @param hooks           生命周期钩子列表（可为 null）
     * @param interceptors    输入拦截器列表（可为 null）
     * @param config          运行配置
     */
    public AgentLoop(
            LLMProvider provider,
            ToolRegistry toolRegistry,
            MessageBuilder messageBuilder,
            List<AgentHook> hooks,
            List<InputInterceptor> interceptors,
            AppConfig config) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.messageBuilder = messageBuilder;
        this.hooks = hooks != null ? hooks : Collections.<AgentHook>emptyList();
        this.interceptors = interceptors != null ? interceptors : Collections.<InputInterceptor>emptyList();
        this.config = config;
    }

    /** 同步模式：发送用户消息并阻塞等待最终响应 */
    public String chat(String userMessage) {
        // 1. 通知循环开始
        for (AgentHook h : hooks) {
            h.onLoopStart(userMessage);
        }

        // 2. 拦截器检查，短路返回
        for (InputInterceptor ic : interceptors) {
            Optional<String> direct = ic.intercept(userMessage);
            if (direct.isPresent()) {
                for (AgentHook h : hooks) {
                    h.onLoopEnd(direct.get());
                }
                return direct.get();
            }
        }

        // 3. 准备消息
        prepareMessages(userMessage);

        // 4. 迭代循环
        for (int iter = 0; iter < config.getMaxIterations(); iter++) {
            for (AgentHook h : hooks) {
                h.beforeLLMCall(messages);
            }
            LLMResponse response = provider.chat(
                    messages,
                    toolRegistry.getDefinitions(),
                    null, null, null
            );
            for (AgentHook h : hooks) {
                h.afterLLMCall(response);
            }

            // 4.1 有 tool_calls 则执行工具并继续循环
            if (response.hasToolCalls()) {
                for (AgentHook h : hooks) {
                    h.onThinking(response.getContent(), response.getReasoningContent());
                }
                messageBuilder.addAssistantMessage(messages, response);
                for (ToolCallRequest tc : response.getToolCalls()) {
                    Tool tool = toolRegistry.getTool(tc.getName());
                    String formattedArgs = tool != null ? tool.formatArgsPreview(tc.getArguments()) : "";
                    String result = toolRegistry.execute(tc);
                    for (AgentHook h : hooks) {
                        h.onToolCall(tc, result, formattedArgs);
                    }
                    messageBuilder.addToolResult(messages, tc, result);
                }
                injectPendingUserMessages();
            } else {
                // 4.2 无 tool_calls 则返回最终响应
                String finalResponse = response.getContent() != null ? response.getContent() : "";
                for (AgentHook h : hooks) {
                    h.onLoopEnd(finalResponse);
                }
                messageBuilder.addAssistantMessage(messages, response);
                return finalResponse;
            }
        }

        // 5. 达到最大迭代次数
        String maxIterMsg = "[Reached max iterations: " + config.getMaxIterations() + "]";
        for (AgentHook h : hooks) {
            h.onLoopEnd(true, maxIterMsg);
        }
        return maxIterMsg;
    }

    /**
     * 流式模式：发送用户消息并通过回调逐块返回内容。
     * <p>
     * 与 {@link #chat} 流程相同，但 LLM 响应通过 StreamConsumer 异步回调。
     */
    public String chatStream(String userMessage, final LLMProvider.StreamConsumer consumer) {
        for (AgentHook h : hooks) {
            h.onLoopStart(userMessage);
        }

        for (InputInterceptor ic : interceptors) {
            Optional<String> direct = ic.intercept(userMessage);
            if (direct.isPresent()) {
                for (AgentHook h : hooks) {
                    h.onLoopEnd(direct.get());
                }
                return direct.get();
            }
        }

        prepareMessages(userMessage);

        for (int iter = 0; iter < config.getMaxIterations(); iter++) {
            for (AgentHook h : hooks) {
                h.beforeLLMCall(messages);
            }

            final LLMResponse[] responseHolder = new LLMResponse[1];
            LLMProvider.StreamConsumer accumulatingConsumer = new LLMProvider.StreamConsumer() {
                @Override
                public void onContent(String chunk) {
                    if (consumer != null) {
                        consumer.onContent(chunk);
                    }
                    for (AgentHook h : hooks) {
                        h.onStreamChunk(chunk);
                    }
                }

                @Override
                public void onToolCall(ToolCallRequest toolCall) {
                    if (consumer != null) {
                        consumer.onToolCall(toolCall);
                    }
                }

                @Override
                public void onComplete(LLMResponse response) {
                    responseHolder[0] = response;
                    if (consumer != null) {
                        consumer.onComplete(response);
                    }
                }

                @Override
                public void onError(String error) {
                    if (consumer != null) {
                        consumer.onError(error);
                    }
                }
            };

            provider.chatStream(
                    messages,
                    toolRegistry.getDefinitions(),
                    null, null, null,
                    accumulatingConsumer
            );

            LLMResponse response = responseHolder[0];
            if (response == null) {
                String err = "[Error: streaming returned no response]";
                for (AgentHook h : hooks) {
                    h.onLoopEnd(err);
                }
                return err;
            }

            for (AgentHook h : hooks) {
                h.afterLLMCall(response);
            }

            if (response.hasToolCalls()) {
                for (AgentHook h : hooks) {
                    h.onThinking(response.getContent(), response.getReasoningContent());
                }
                messageBuilder.addAssistantMessage(messages, response);
                for (ToolCallRequest tc : response.getToolCalls()) {
                    Tool tool = toolRegistry.getTool(tc.getName());
                    String formattedArgs = tool != null ? tool.formatArgsPreview(tc.getArguments()) : "";
                    String result = toolRegistry.execute(tc);
                    for (AgentHook h : hooks) {
                        h.onToolCall(tc, result, formattedArgs);
                    }
                    messageBuilder.addToolResult(messages, tc, result);
                }
                injectPendingUserMessages();
            } else {
                String finalResponse = response.getContent() != null ? response.getContent() : "";
                for (AgentHook h : hooks) {
                    h.onLoopEnd(finalResponse);
                }
                return finalResponse;
            }
        }

        String maxIterMsg = "[Reached max iterations: " + config.getMaxIterations() + "]";
        for (AgentHook h : hooks) {
            h.onLoopEnd(maxIterMsg);
        }
        return maxIterMsg;
    }

    /** 在运行中注入新的用户消息，将在下一轮 LLM 调用前加入消息历史。 */
    public void injectUserMessage(String userMessage) {
        pendingUserMessages.offer(userMessage);
    }

    /** 重置会话状态，清空消息历史和待注入消息队列。 */
    public void reset() {
        this.messages = null;
        this.pendingUserMessages.clear();
    }

    /** 将等待中的用户消息注入消息历史并通知钩子。 */
    private void injectPendingUserMessages() {
        String msg;
        while ((msg = pendingUserMessages.poll()) != null) {
            if (messages != null) {
                messageBuilder.addUserMessage(messages, msg);
            }
            for (AgentHook h : hooks) {
                h.onUserMessageInjected(msg);
            }
        }
    }

    /** 首次调用初始化消息列表，后续调用追加用户消息（支持多轮对话持久化） */
    private void prepareMessages(String userMessage) {
        if (messages == null) {
            messages = messageBuilder.buildInitial(userMessage);
        } else {
            messageBuilder.addUserMessage(messages, userMessage);
        }
    }
}
