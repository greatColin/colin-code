package com.coloop.agent.core.agent;

import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.context.ConversationSummary;
import com.coloop.agent.core.context.ContextCompactor;
import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.message.MessageBuilder;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.core.util.TokenEstimator;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

    /** 请求停止当前循环 */
    private volatile boolean stopRequested = false;

    private ContextCompactor compactor;
    private ConversationState conversationState;
    private ConversationSummary currentSummary;

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
        stopRequested = false;

        // 4. 迭代循环
        for (int iter = 0; iter < config.getMaxIterations(); iter++) {
            if (stopRequested) {
                String stopMsg = "[Stopped by user]";
                for (AgentHook h : hooks) {
                    h.onLoopEnd(stopMsg);
                }
                return stopMsg;
            }
            for (AgentHook h : hooks) {
                h.beforeLLMCall(messages);
            }
            checkAndAutoCompact();
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
                messageBuilder.addAssistantMessage(messages, response);
                for (AgentHook h : hooks) {
                    h.onLoopEnd(finalResponse);
                }
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
        stopRequested = false;

        for (int iter = 0; iter < config.getMaxIterations(); iter++) {
            if (stopRequested) {
                String stopMsg = "[Stopped by user]";
                for (AgentHook h : hooks) {
                    h.onLoopEnd(stopMsg);
                }
                return stopMsg;
            }
            for (AgentHook h : hooks) {
                h.beforeLLMCall(messages);
            }
            checkAndAutoCompact();

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
                messageBuilder.addAssistantMessage(messages, response);
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

    /** 请求停止当前正在执行的循环。 */
    public void requestStop() {
        this.stopRequested = true;
    }

    /** 当前循环是否已被请求停止。 */
    public boolean isStopRequested() {
        return this.stopRequested;
    }

    /** 重置会话状态，清空消息历史和待注入消息队列。 */
    public void reset() {
        this.messages = null;
        this.pendingUserMessages.clear();
        this.currentSummary = null;
        if (this.conversationState != null) {
            this.conversationState.setSummary(null);
        }
    }

    /** 设置上下文压缩器。 */
    public void setContextCompactor(ContextCompactor compactor) {
        this.compactor = compactor;
    }

    /** 设置会话状态共享对象。 */
    public void setConversationState(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    /** 获取会话状态共享对象。 */
    public ConversationState getConversationState() {
        return conversationState;
    }

    /** 获取当前消息列表的估算 token 数。 */
    public int getCurrentTokenCount() {
        return TokenEstimator.estimate(messages);
    }

    /** 获取配置的最大上下文 token 数。 */
    public int getContextLimit() {
        return config.getMaxContextSize();
    }

    /** 获取当前上下文占用百分比（0-100）。 */
    public int getContextUsagePercent() {
        int limit = getContextLimit();
        if (limit <= 0) return 0;
        return Math.min(100, (int) ((getCurrentTokenCount() * 100L) / limit));
    }

    /**
     * 压缩上下文：将历史消息生成摘要并注入 system prompt，释放 token 窗口。
     *
     * <p>默认不保留最近轮次（keepLastN=0）。
     * 若配置了 {@link ContextCompactor}，则调用其生成摘要；否则仅重置。</p>
     */
    public void compact() {
        compact(0);
    }

    /**
     * 压缩上下文，保留最近指定轮数的消息不压缩。
     *
     * @param keepLastN 保留最近多少条非 system 消息
     */
    public void compact(int keepLastN) {
        if (compactor == null || messages == null || messages.isEmpty()) {
            return;
        }

        // 分离 system message、待压缩部分和保留部分
        List<Map<String, Object>> systemMessages = new ArrayList<>();
        List<Map<String, Object>> toSummarize = new ArrayList<>();
        List<Map<String, Object>> toKeep = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemMessages.add(msg);
            } else {
                toSummarize.add(msg);
            }
        }

        if (toSummarize.size() <= keepLastN) {
            return;
        }

        // 将末尾 keepLastN 条移到 toKeep
        while (toSummarize.size() > keepLastN) {
            toKeep.add(toSummarize.remove(toSummarize.size() - 1));
        }
        // 恢复 toKeep 顺序
        Collections.reverse(toKeep);

        if (toSummarize.isEmpty()) {
            return;
        }

        ConversationSummary summary = compactor.compact(toSummarize);
        if (summary == null || summary.getContent() == null || summary.getContent().isEmpty()) {
            return;
        }

        this.currentSummary = summary;
        if (this.conversationState != null) {
            this.conversationState.setSummary(summary);
        }

        // 重建消息列表：system + 摘要注入 + 保留消息
        messages = new ArrayList<>();
        if (!systemMessages.isEmpty()) {
            Map<String, Object> sys = new HashMap<>(systemMessages.get(0));
            injectSummaryIntoSystemMessage(sys, summary.getContent());
            messages.add(sys);
        }
        messages.addAll(toKeep);
    }

    /** 检查并自动压缩上下文（当占用超过阈值时）。 */
    private void checkAndAutoCompact() {
        int limit = config.getMaxContextSize();
        if (limit <= 0 || compactor == null) {
            return;
        }
        int current = getCurrentTokenCount();
        if (current > limit * 0.8) {
            compact(2); // 自动压缩时保留最近 2 轮
        }
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

    private static final String SUMMARY_MARKER = "\n\n## 对话摘要\n";

    private void injectSummaryIntoSystemMessage(Map<String, Object> systemMsg, String summary) {
        String content = (String) systemMsg.get("content");
        if (content == null) content = "";
        int idx = content.indexOf(SUMMARY_MARKER);
        if (idx >= 0) {
            content = content.substring(0, idx) + SUMMARY_MARKER + summary;
        } else {
            content = content + SUMMARY_MARKER + summary;
        }
        systemMsg.put("content", content);
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
