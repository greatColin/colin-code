package com.coloop.agent.capability.subagent;

/**
 * Listener for subagent lifecycle events.
 */
public interface SubagentEventListener {
    void onCreated(SubagentInstance inst);
    void onCleared(String name);
}
