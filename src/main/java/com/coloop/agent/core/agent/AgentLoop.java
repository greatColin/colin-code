package com.coloop.agent.core.agent;

import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.message.MessageBuilder;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentLoop {

    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final MessageBuilder messageBuilder;
    private final List<AgentHook> hooks;
    private final List<InputInterceptor> interceptors;
    private final AppConfig config;

    private List<Map<String, Object>> messages;

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

    public String chat(String userMessage) {
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
            LLMResponse response = provider.chat(
                    messages,
                    toolRegistry.getDefinitions(),
                    config.getModel(),
                    config.getMaxTokens(),
                    config.getTemperature()
            );
            for (AgentHook h : hooks) {
                h.afterLLMCall(response);
            }

            if (response.hasToolCalls()) {
                messageBuilder.addAssistantMessage(messages, response);
                for (ToolCallRequest tc : response.getToolCalls()) {
                    String result = toolRegistry.execute(tc);
                    for (AgentHook h : hooks) {
                        h.onToolCall(tc, result);
                    }
                    messageBuilder.addToolResult(messages, tc, result);
                }
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
                    config.getModel(),
                    config.getMaxTokens(),
                    config.getTemperature(),
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
                messageBuilder.addAssistantMessage(messages, response);
                for (ToolCallRequest tc : response.getToolCalls()) {
                    String result = toolRegistry.execute(tc);
                    for (AgentHook h : hooks) {
                        h.onToolCall(tc, result);
                    }
                    messageBuilder.addToolResult(messages, tc, result);
                }
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

    private void prepareMessages(String userMessage) {
        if (messages == null) {
            messages = messageBuilder.buildInitial(userMessage);
        } else {
            messageBuilder.addUserMessage(messages, userMessage);
        }
    }
}
