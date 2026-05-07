package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentLoop;
import java.util.List;

/**
 * Factory that creates a subagent AgentLoop with given parameters.
 * Server layer provides the closure holding LLMProvider, parent tools, session, config.
 */
@FunctionalInterface
public interface SubagentLoopFactory {
    /**
     * @param name        subagent name (passed to SubagentLoggingHook)
     * @param systemPrompt system prompt for this subagent
     * @param toolNames   tool name whitelist; null means inherit all parent tools
     * @return configured AgentLoop ready for chatStream()
     */
    AgentLoop create(String name, String systemPrompt, List<String> toolNames);
}
