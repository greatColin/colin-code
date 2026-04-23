package com.coloop.agent.capability.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.util.TokenEstimator;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.List;
import java.util.Map;

public class LoggingHook implements AgentHook {

    private final AppConfig config;
    private int lastTokenCount = 0;
    private int lastContextLimit = 0;
    private int lastUsagePercent = 0;

    public LoggingHook() {
        this(null);
    }

    public LoggingHook(AppConfig config) {
        this.config = config;
    }

    @Override
    public void onLoopStart(String userMessage) {
        System.out.println("[USER INPUT] " + userMessage);
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        lastTokenCount = TokenEstimator.estimate(messages);
        lastContextLimit = config != null ? config.getMaxContextSize() : 0;
        if (lastContextLimit > 0) {
            lastUsagePercent = Math.min(100, (int) ((lastTokenCount * 100L) / lastContextLimit));
        } else {
            lastUsagePercent = 0;
        }
        System.out.println("[CONTEXT] " + lastTokenCount + "/" + lastContextLimit + " tokens (" + lastUsagePercent + "%)");
    }

    @Override
    public void afterLLMCall(LLMResponse response) {
//        System.out.println("[LOG] After LLM call, hasToolCalls: " + response.hasToolCalls());
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        if (formattedArgs != null && !formattedArgs.isEmpty()) {
            System.out.println("[TOOL EXECUTED] " + toolCall.getName() + "(" + formattedArgs + ")");
        } else {
            System.out.println("[TOOL EXECUTED] " + toolCall.getName());
        }
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            System.out.println("[THINKING REASON] " + reasoningContent);
        }
        if (content != null && !content.isEmpty()) {
            System.out.println("[THINKING] " + content);
        }
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if(!maxIte) {
            // 输出agent回答
            System.out.println("[LOOP RESULT] " + finalResponse);
        } else {
            System.out.println("[LOOP ERROR] Loop end. max loop");
        }
    }

    @Override
    public void onUserMessageInjected(String message) {
        System.out.println("[USER INPUT] " + message);
    }
}
