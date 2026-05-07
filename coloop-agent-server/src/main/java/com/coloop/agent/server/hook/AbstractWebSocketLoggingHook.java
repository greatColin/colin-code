package com.coloop.agent.server.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Base class for WebSocket logging hooks.
 * Contains shared send logic. Subclasses override whether agentName is set.
 */
public abstract class AbstractWebSocketLoggingHook implements AgentHook {

    protected final WebSocketSession session;
    protected final ObjectMapper objectMapper;
    protected AgentLoop agentLoop;

    protected AbstractWebSocketLoggingHook(WebSocketSession session) {
        this.session = session;
        this.objectMapper = new ObjectMapper();
    }

    public void setAgentLoop(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    protected abstract String getAgentName();

    protected void send(WebSocketMessage msg) {
        if (!session.isOpen()) return;
        try {
            String agentName = getAgentName();
            if (agentName != null) {
                msg.withAgent(agentName);
            }
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("WebSocket send failed: " + e.getMessage());
        }
    }
}
