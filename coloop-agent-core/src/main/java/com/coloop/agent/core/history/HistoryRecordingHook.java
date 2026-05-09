package com.coloop.agent.core.history;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;

/**
 * Captures AgentLoop lifecycle events and persists them to ConversationHistoryStore.
 * Both main agent and subagents share the same store instance but pass their own agentName.
 */
public class HistoryRecordingHook implements AgentHook {

    private final ConversationHistoryStore store;
    private final String sessionId;
    private final String agentName;
    private boolean titleGenerated = false;
    private final java.util.function.Consumer<String> titleUpdateListener;

    public HistoryRecordingHook(ConversationHistoryStore store, String sessionId, String agentName) {
        this(store, sessionId, agentName, null);
    }

    public HistoryRecordingHook(ConversationHistoryStore store, String sessionId, String agentName, java.util.function.Consumer<String> titleUpdateListener) {
        this.store = store;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.titleUpdateListener = titleUpdateListener;
    }

    @Override
    public void onLoopStart(String userMessage) {
        store.saveMessage(sessionId, HistoryMessage.user(agentName, userMessage));
        generateTitleIfNeeded(userMessage);
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        store.saveMessage(sessionId, HistoryMessage.thinking(agentName, content, reasoningContent));
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        String fullArgs = "{}";
        try {
            fullArgs = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(toolCall.getArguments());
        } catch (Exception e) {
            // ignore serialization error
        }
        store.saveMessage(sessionId, HistoryMessage.toolCall(agentName, toolCall.getName(), formattedArgs, fullArgs));
        boolean success = result != null && !result.startsWith("Error:");
        store.saveMessage(sessionId, HistoryMessage.toolResult(agentName, toolCall.getName(), result, success));
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if (maxIte) {
            store.saveMessage(sessionId, HistoryMessage.system(agentName,
                    "[Reached max iterations: " + finalResponse + "]"));
        } else {
            store.saveMessage(sessionId, HistoryMessage.assistant(agentName, finalResponse));
        }
    }

    @Override
    public void onUserMessageInjected(String message) {
        store.saveMessage(sessionId, HistoryMessage.user(agentName, message));
        generateTitleIfNeeded(message);
    }

    @Override
    public void onContextUsage(int tokens, int limit, int percent) {
        store.saveMessage(sessionId, HistoryMessage.contextUsage(agentName, tokens, limit, percent));
    }

    private void generateTitleIfNeeded(String userMessage) {
        if (titleGenerated || userMessage == null || userMessage.trim().isEmpty()) return;
        String trimmed = userMessage.trim();
        String title = trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed;
        store.updateTitle(sessionId, title);
        titleGenerated = true;
        if (titleUpdateListener != null) {
            titleUpdateListener.accept(title);
        }
    }
}
