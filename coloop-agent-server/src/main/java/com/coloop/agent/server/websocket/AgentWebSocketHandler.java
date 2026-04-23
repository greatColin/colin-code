package com.coloop.agent.server.websocket;

import com.coloop.agent.server.service.AgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public AgentWebSocketHandler(AgentService agentService) {
        this.agentService = agentService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("[WebSocket] Connected: " + session.getId());
        agentService.sendAvailableCommands(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String action = jsonNode.path("action").asText("");

            if ("chat".equals(action)) {
                String userMessage = jsonNode.path("message").asText("");
                if (!userMessage.isEmpty()) {
                    agentService.startChat(userMessage, session);
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("[WebSocket] Disconnected: " + session.getId() + " status=" + status);
        agentService.removeSession(session.getId());
    }
}
