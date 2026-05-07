package com.coloop.agent.server.hook;

import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.server.dto.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Subagent logging hook. All events carry the subagent's name
 * so the frontend can route them to the correct panel.
 */
public class SubagentLoggingHook extends AbstractWebSocketLoggingHook {

    private final String agentName;

    public SubagentLoggingHook(WebSocketSession session, String agentName) {
        super(session);
        this.agentName = agentName;
    }

    @Override
    protected String getAgentName() {
        return agentName;
    }

    // --- Lifecycle overrides (same as WebSocketLoggingHook, without task/plan) ---

    @Override
    public void onLoopStart(String userMessage) {
        send(WebSocketMessage.user(userMessage));
    }

    @Override
    public void beforeLLMCall(java.util.List<java.util.Map<String, Object>> messages) {
        if (agentLoop != null) {
            int tokens = agentLoop.getCurrentTokenCount();
            int limit = agentLoop.getContextLimit();
            int pct = agentLoop.getContextUsagePercent();
            System.out.println("[SubagentLoggingHook] " + agentName + " context_usage: " + tokens + "/" + limit + " (" + pct + "%)");
            send(WebSocketMessage.contextUsage(tokens, limit, pct));
        } else {
            System.out.println("[SubagentLoggingHook] " + agentName + " beforeLLMCall: agentLoop is null");
        }
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        send(WebSocketMessage.thinking(content, reasoningContent));
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        try {
            String fullArgs = objectMapper.writeValueAsString(toolCall.getArguments());
            send(WebSocketMessage.toolCall(toolCall.getName(), formattedArgs, fullArgs));
        } catch (Exception e) {
            System.err.println("Failed to serialize tool arguments: " + e.getMessage());
            send(WebSocketMessage.toolCall(toolCall.getName(), formattedArgs, "{}"));
        }
        boolean success = result != null && !result.startsWith("Error:");
        send(WebSocketMessage.toolResult(toolCall.getName(), result, success));
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if (maxIte) {
            send(WebSocketMessage.system(finalResponse));
        } else {
            send(WebSocketMessage.assistant(finalResponse));
        }
        if (agentLoop != null) {
            int tokens = agentLoop.getCurrentTokenCount();
            int limit = agentLoop.getContextLimit();
            int pct = agentLoop.getContextUsagePercent();
            System.out.println("[SubagentLoggingHook] " + agentName + " onLoopEnd context_usage: " + tokens + "/" + limit + " (" + pct + "%)");
            send(WebSocketMessage.contextUsage(tokens, limit, pct));
        } else {
            System.out.println("[SubagentLoggingHook] " + agentName + " onLoopEnd: agentLoop is null");
        }
    }

    @Override
    public void onUserMessageInjected(String message) {
        send(WebSocketMessage.user(message));
    }

    @Override
    public void onStreamChunk(String chunk) {
        send(WebSocketMessage.streamChunk(chunk));
    }
}
