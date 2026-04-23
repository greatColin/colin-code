package com.coloop.agent.server.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketMessageTest {

    @Test
    void testCommandsMessageFactory() {
        Map<String, String> cmd1 = new HashMap<>();
        cmd1.put("name", "exit");
        cmd1.put("description", "Exit the application");

        Map<String, String> cmd2 = new HashMap<>();
        cmd2.put("name", "new");
        cmd2.put("description", "Start a new session");

        List<Map<String, String>> commands = Arrays.asList(cmd1, cmd2);

        WebSocketMessage msg = WebSocketMessage.commands(commands);

        assertEquals("commands", msg.getType());
        assertNotNull(msg.getPayload());
        assertNotNull(msg.getTimestamp());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payloadCommands = (List<Map<String, Object>>) msg.getPayload().get("commands");
        assertEquals(2, payloadCommands.size());
        assertEquals("exit", payloadCommands.get(0).get("name"));
        assertEquals("new", payloadCommands.get(1).get("name"));
    }

    @Test
    void testCommandsMessageWithEmptyList() {
        WebSocketMessage msg = WebSocketMessage.commands(Arrays.asList());

        assertEquals("commands", msg.getType());
        @SuppressWarnings("unchecked")
        List<?> payloadCommands = (List<?>) msg.getPayload().get("commands");
        assertTrue(payloadCommands.isEmpty());
    }
}
