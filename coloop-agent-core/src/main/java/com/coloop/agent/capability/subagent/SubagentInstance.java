package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentLoop;
import java.util.List;

/**
 * Immutable snapshot of a subagent's identity and runtime state.
 *
 * <p>Identity fields ({@code name}, {@code description}, {@code systemPrompt},
 * {@code toolNames}) are set once at construction and never change.
 * {@code toolNames} may be {@code null}, meaning the subagent inherits
 * the full parent toolset.</p>
 *
 * <p>Runtime state: {@code running} indicates whether the subagent is
 * currently inside a {@code chatStream} call. {@code runLock} is the
 * synchronization object callers use to serialize access to the loop.
 * Both are managed by the caller (AgentTool / SendMessageTool),
 * not by this class.</p>
 */
public final class SubagentInstance {
    public final String name;
    public final String description;
    public final String systemPrompt;
    public final List<String> toolNames;
    public final AgentLoop agentLoop;
    public final long createdAt;
    public volatile boolean running;
    public final Object runLock = new Object();
    /** Whether to include &lt;think&gt; blocks in tool results returned to the parent agent. */
    public final boolean returnThinking;

    public SubagentInstance(String name, String description,
                            String systemPrompt, List<String> toolNames,
                            AgentLoop agentLoop) {
        this(name, description, systemPrompt, toolNames, agentLoop, false);
    }

    public SubagentInstance(String name, String description,
                            String systemPrompt, List<String> toolNames,
                            AgentLoop agentLoop, boolean returnThinking) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.toolNames = toolNames;
        this.agentLoop = agentLoop;
        this.returnThinking = returnThinking;
        this.createdAt = System.currentTimeMillis();
    }
}
