package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.runtime.CompositeCapability;
import java.util.List;

public final class SubagentManagementCapability implements CompositeCapability {

    private final SubagentRegistry registry;
    private final AgentTool agentTool;
    private final SendMessageTool sendMessageTool;

    public SubagentManagementCapability(SubagentLoopFactory factory,
                                         SubagentRegistry registry,
                                         SubagentEventListener listener) {
        this.registry = registry;
        if (listener != null) {
            this.registry.addListener(listener);
        }
        this.agentTool = new AgentTool(registry, factory);
        this.sendMessageTool = new SendMessageTool(registry);
    }

    @Override
    public List<Tool> getTools() {
        return List.of(agentTool, sendMessageTool);
    }

    @Override
    public PromptPlugin getPromptPlugin() {
        return null;
    }

    @Override
    public AgentHook getHook() {
        return null;
    }

    public SubagentRegistry getRegistry() {
        return registry;
    }
}
