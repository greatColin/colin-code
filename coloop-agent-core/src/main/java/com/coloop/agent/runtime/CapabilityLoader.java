package com.coloop.agent.runtime;

import com.coloop.agent.capability.mcp.McpCapability;
import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.message.MessageBuilder;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.runtime.config.AppConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一加载拓展工具，链式加载组件，构造agentLoop
 */
public class CapabilityLoader {

    private final List<Tool> tools = new ArrayList<Tool>();
    private final List<PromptPlugin> promptPlugins = new ArrayList<PromptPlugin>();
    private final List<AgentHook> hooks = new ArrayList<AgentHook>();
    private final List<InputInterceptor> interceptors = new ArrayList<InputInterceptor>();
    private MessageBuilder messageBuilder;
    private com.coloop.agent.core.context.ContextCompactor compactor;
    private com.coloop.agent.core.context.ConversationState conversationState;

    public CapabilityLoader withTool(Tool tool) {
        if (tool != null) {
            tools.add(tool);
        }
        return this;
    }

    public CapabilityLoader withPromptPlugin(PromptPlugin plugin) {
        if (plugin != null) {
            promptPlugins.add(plugin);
        }
        return this;
    }

    public CapabilityLoader withHook(AgentHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
        return this;
    }

    public CapabilityLoader withInterceptor(InputInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
        return this;
    }

    public CapabilityLoader withMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
        return this;
    }

    public CapabilityLoader withContextCompactor(com.coloop.agent.core.context.ContextCompactor compactor) {
        this.compactor = compactor;
        return this;
    }

    public CapabilityLoader withConversationState(com.coloop.agent.core.context.ConversationState conversationState) {
        this.conversationState = conversationState;
        return this;
    }

    public CapabilityLoader withCapability(StandardCapability cap, AppConfig config) {
        Object instance = cap.create(config);
        switch (cap.getType()) {
            case TOOL:
                if (instance instanceof McpCapability) {
                    // MCP Capability returns multiple tools
                    for (Tool tool : ((McpCapability) instance).getTools()) {
                        withTool(tool);
                    }
                } else if (instance instanceof Tool) {
                    withTool((Tool) instance);
                }
                break;
            case PROMPT_PLUGIN:
                withPromptPlugin((PromptPlugin) instance);
                break;
            case HOOK:
                withHook((AgentHook) instance);
                break;
            case INTERCEPTOR:
                withInterceptor((InputInterceptor) instance);
                break;
            case COMPOSITE:
                if (instance instanceof com.coloop.agent.capability.task.TaskManagementCapability) {
                    com.coloop.agent.capability.task.TaskManagementCapability tmc =
                            (com.coloop.agent.capability.task.TaskManagementCapability) instance;
                    for (Tool tool : tmc.getTools()) {
                        withTool(tool);
                    }
                    withPromptPlugin(tmc.getPromptPlugin());
                    withHook(tmc.getHook());
                }
                break;
            default:
                break;
        }
        return this;
    }

    /**
     * 构造最基本llm+tool的agent能力
     *
     * @param provider
     * @param config
     * @return
     */
    public @NotNull AgentLoop build(LLMProvider provider, AppConfig config) {
        ToolRegistry registry = new ToolRegistry();
        for (Tool t : tools) {
            registry.register(t);
        }

        // 自动创建共享会话状态
        if (this.conversationState == null) {
            this.conversationState = new com.coloop.agent.core.context.ConversationState();
        }

        // 为 SummaryPromptPlugin 注入会话状态
        for (PromptPlugin plugin : promptPlugins) {
            if (plugin instanceof com.coloop.agent.capability.prompt.SummaryPromptPlugin) {
                ((com.coloop.agent.capability.prompt.SummaryPromptPlugin) plugin).setConversationState(conversationState);
            }
        }

        MessageBuilder mb = this.messageBuilder;
        if (mb == null) {
            mb = new com.coloop.agent.capability.message.StandardMessageBuilder(promptPlugins, config);
        }

        AgentLoop agentLoop = new AgentLoop(
                provider, registry, mb, hooks, interceptors, config
        );
        agentLoop.setConversationState(conversationState);

        // 自动创建默认 compactor（若未手动设置且 provider 可用）
        if (this.compactor == null && provider != null) {
            agentLoop.setContextCompactor(new com.coloop.agent.capability.context.LLMContextCompactor(provider));
        } else if (this.compactor != null) {
            agentLoop.setContextCompactor(this.compactor);
        }

        return agentLoop;
    }
}
