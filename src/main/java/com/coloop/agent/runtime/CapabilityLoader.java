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

        MessageBuilder mb = this.messageBuilder;
        if (mb == null) {
            mb = new com.coloop.agent.capability.message.StandardMessageBuilder(promptPlugins, config);
        }

        return new AgentLoop(
                provider, registry, mb, hooks, interceptors, config
        );
    }
}
