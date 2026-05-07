package com.coloop.agent.server.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketMessageAgentNameTest {

    @Test
    void testAgentNameDefaultsToNull() {
        WebSocketMessage msg = new WebSocketMessage("test", null);
        assertNull(msg.getAgentName());
    }

    @Test
    void testWithAgentSetsAgentName() {
        WebSocketMessage msg = new WebSocketMessage("test", null);
        WebSocketMessage chained = msg.withAgent("planner");
        assertSame(msg, chained);
        assertEquals("planner", msg.getAgentName());
    }

    @Test
    void testSubagentCreatedFactory() {
        WebSocketMessage msg = WebSocketMessage.subagentCreated("helper", "Helps with tasks", "summary text");
        assertEquals("subagent_created", msg.getType());
        assertEquals("helper", msg.getPayload().get("name"));
        assertEquals("Helps with tasks", msg.getPayload().get("description"));
        assertEquals("summary text", msg.getPayload().get("summary"));
    }

    @Test
    void testSubagentClearedFactory() {
        WebSocketMessage msg = WebSocketMessage.subagentCleared("planner");
        assertEquals("subagent_cleared", msg.getType());
        assertEquals("planner", msg.getPayload().get("name"));
    }

    @Test
    void testExistingFactoriesDontSetAgentName() {
        WebSocketMessage user = WebSocketMessage.user("hello");
        assertNull(user.getAgentName());
        WebSocketMessage error = WebSocketMessage.error("oops");
        assertNull(error.getAgentName());
    }
}
