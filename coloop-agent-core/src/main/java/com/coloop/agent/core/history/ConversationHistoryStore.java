package com.coloop.agent.core.history;

import java.util.List;

public interface ConversationHistoryStore {
    String createSession();
    void saveMessage(String sessionId, HistoryMessage message);
    SessionMeta loadSessionMeta(String sessionId);
    List<HistoryMessage> loadMessages(String sessionId);
    List<SessionMeta> listSessions();
    void updateTitle(String sessionId, String title);
}
