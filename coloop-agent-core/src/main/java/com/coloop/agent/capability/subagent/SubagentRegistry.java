package com.coloop.agent.capability.subagent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for subagent instances.
 *
 * <p>Supports create-or-replace semantics: if a subagent with the same name
 * already exists, it is cleared (firing {@code onCleared}) before the new
 * one is registered (firing {@code onCreated}). The {@code createOrReplace}
 * method is synchronized to guarantee atomicity.</p>
 */
public final class SubagentRegistry {
    private final ConcurrentMap<String, SubagentInstance> map = new ConcurrentHashMap<>();
    private final List<SubagentEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Register or replace a subagent instance.
     *
     * <p>If an instance with the same name already exists, it is removed
     * and {@link SubagentEventListener#onCleared(String)} is fired before
     * the new instance is stored and {@link SubagentEventListener#onCreated(SubagentInstance)}
     * is fired.</p>
     */
    public void createOrReplace(String name, SubagentInstance instance) {
        synchronized (this) {
            SubagentInstance old = map.remove(name);
            map.put(name, instance);
            if (old != null) {
                for (SubagentEventListener l : listeners) {
                    l.onCleared(name);
                }
            }
            for (SubagentEventListener l : listeners) {
                l.onCreated(instance);
            }
        }
    }

    /** Get a subagent by name, or {@code null} if not found. */
    public SubagentInstance get(String name) {
        return map.get(name);
    }

    /** Remove a subagent by name. Does not fire any listener events. */
    public void remove(String name) {
        map.remove(name);
    }

    /** Return a snapshot of all currently registered subagent instances. */
    public List<SubagentInstance> snapshot() {
        return new ArrayList<>(map.values());
    }

    /**
     * Remove all subagents, firing {@link SubagentEventListener#onCleared(String)}
     * for each removed entry.
     */
    public void clear() {
        List<String> names = new ArrayList<>(map.keySet());
        map.clear();
        for (String name : names) {
            for (SubagentEventListener l : listeners) {
                l.onCleared(name);
            }
        }
    }

    /** Register a lifecycle event listener. */
    public void addListener(SubagentEventListener l) {
        listeners.add(l);
    }
}
