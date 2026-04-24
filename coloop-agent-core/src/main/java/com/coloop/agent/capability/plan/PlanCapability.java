package com.coloop.agent.capability.plan;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.runtime.CompositeCapability;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.List;

public class PlanCapability implements CompositeCapability {

    private final ConversationState conversationState;
    private final PlanCommand planCommand;
    private final CancelCommand cancelCommand;
    private final PlanPromptPlugin planPromptPlugin;
    private final PlanInjectionHook planInjectionHook;

    public PlanCapability(LLMProvider provider, AppConfig config) {
        this.conversationState = new ConversationState();
        this.planCommand = new PlanCommand(provider, config, conversationState);
        this.cancelCommand = new CancelCommand(conversationState);
        this.planPromptPlugin = new PlanPromptPlugin();
        this.planInjectionHook = new PlanInjectionHook(conversationState);
    }

    @Override
    public List<Tool> getTools() {
        return List.of();
    }

    @Override
    public PromptPlugin getPromptPlugin() {
        return planPromptPlugin;
    }

    @Override
    public AgentHook getHook() {
        return planInjectionHook;
    }

    public PlanCommand getPlanCommand() {
        return planCommand;
    }

    public CancelCommand getCancelCommand() {
        return cancelCommand;
    }
}
