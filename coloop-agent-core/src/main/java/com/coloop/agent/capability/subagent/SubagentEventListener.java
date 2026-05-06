package com.coloop.agent.capability.subagent;

/**
 * Listener for subagent lifecycle events.
 *
 * <p>Implementations can override individual methods as needed; all have
 * default empty implementations. Events are fired synchronously from
 * within {@link SubagentRegistry} operations.</p>
 *
 * <p>Lifecycle order: {@link #onCreated(SubagentInstance)} fires when a
 * new subagent is registered (including replacements); {@link #onCleared(String)}
 * fires when a subagent is removed by name (clear, replace, or explicit remove).</p>
 */
public interface SubagentEventListener {

    /** Called when a subagent instance is registered (new or replacement). */
    default void onCreated(SubagentInstance instance) {}

    /** Called when a subagent is removed by name. The instance is already disposed. */
    default void onCleared(String name) {}
}
