package com.coloop.agent.core.history;

/**
 * Session metadata for conversation history indexing.
 */
public class SessionMeta {
    public String id;
    public String title;
    public long createdAt;
    public long updatedAt;

    public SessionMeta() {}

    public SessionMeta(String id, String title, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
