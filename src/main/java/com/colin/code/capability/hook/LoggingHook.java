package com.colin.code.capability.hook;

import com.colin.code.core.agent.AgentHook;
import com.colin.code.core.provider.LLMResponse;
import com.colin.code.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;

public class LoggingHook implements AgentHook {
    @Override
    public void onLoopStart(String userMessage) {
        System.out.println("[LOG] Loop start: " + userMessage);
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        System.out.println("[LOG] Before LLM call, messages: " + messages.size());
    }

    @Override
    public void afterLLMCall(LLMResponse response) {
        System.out.println("[LOG] After LLM call, hasToolCalls: " + response.hasToolCalls());
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result) {
        System.out.println("[LOG] Tool executed: " + toolCall.getName());
    }

    @Override
    public void onLoopEnd(String finalResponse) {
        System.out.println("[LOG] Loop end.");
    }
}
