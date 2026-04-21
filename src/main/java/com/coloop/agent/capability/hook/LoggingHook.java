package com.coloop.agent.capability.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;

public class LoggingHook implements AgentHook {
    @Override
    public void onLoopStart(String userMessage) {
        System.out.println("[USER INPUT] " + userMessage);
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
//        System.out.println("[LOG] Before LLM call, messages: " + messages.size());
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
}
