package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentLoop;
import java.util.List;

public final class SubagentInstance {
    public final String name;
    public final String description;
    public final String systemPrompt;
    public final List<String> toolNames;
    public final AgentLoop agentLoop;
    public final long createdAt;
    public volatile boolean running;
    public final Object runLock = new Object();

    public SubagentInstance(String name, String description,
                            String systemPrompt, List<String> toolNames,
                            AgentLoop agentLoop) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.toolNames = toolNames;
        this.agentLoop = agentLoop;
        this.createdAt = System.currentTimeMillis();
    }
}
