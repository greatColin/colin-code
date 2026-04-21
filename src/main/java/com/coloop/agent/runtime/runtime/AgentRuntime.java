package com.coloop.agent.runtime.runtime;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMProvider;

public class AgentRuntime {
    private final AgentLoop agentLoop;

    public AgentRuntime(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    public String chat(String input) {
        return agentLoop.chat(input);
    }

    public String chatStream(String input, LLMProvider.StreamConsumer consumer) {
        return agentLoop.chatStream(input, consumer);
    }
}
