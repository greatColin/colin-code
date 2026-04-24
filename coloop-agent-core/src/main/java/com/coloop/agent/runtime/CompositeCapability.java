package com.coloop.agent.runtime;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.tool.Tool;

import java.util.List;

/**
 * Composite capability that bundles multiple capability types (tools, plugins, hooks).
 * Allows a single capability to contribute multiple component kinds at once.
 */
public interface CompositeCapability {
    List<Tool> getTools();
    PromptPlugin getPromptPlugin();
    AgentHook getHook();
}
