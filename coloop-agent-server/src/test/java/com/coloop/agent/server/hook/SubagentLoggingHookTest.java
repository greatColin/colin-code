package com.coloop.agent.server.hook;

import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SubagentLoggingHookTest {

    private FakeSession fakeSession;
    private SubagentLoggingHook hook;

    @BeforeEach
    void setUp() {
        fakeSession = new FakeSession();
        hook = new SubagentLoggingHook(fakeSession, "planner");
    }

    @Test
    void testGetAgentNameReturnsPlanner() {
        assertEquals("planner", hook.getAgentName());
    }

    @Test
    void testEventsIncludeAgentName() throws Exception {
        ObjectMapper om = new ObjectMapper();

        hook.onLoopStart("hello");
        String json = fakeSession.messages.get(0);
        com.coloop.agent.server.dto.WebSocketMessage msg = om.readValue(json, com.coloop.agent.server.dto.WebSocketMessage.class);
        assertEquals("planner", msg.getAgentName());
        assertEquals("user", msg.getType());

        hook.onStreamChunk("chunky");
        json = fakeSession.messages.get(1);
        msg = om.readValue(json, com.coloop.agent.server.dto.WebSocketMessage.class);
        assertEquals("planner", msg.getAgentName());
        assertEquals("stream_chunk", msg.getType());
    }

    @Test
    void testOnToolCallSendsToolAndResult() {
        ToolCallRequest req = new ToolCallRequest();
        req.setName("read");
        req.setArguments(Map.of("file", "test.txt"));
        hook.onToolCall(req, "file content", "file=test.txt");
        assertEquals(2, fakeSession.messages.size()); // tool_call + tool_result
    }

    @Test
    void testOnLoopEndSendsAssistant() {
        hook.onLoopEnd(false, "final answer");
        String json = fakeSession.messages.get(0);
        assertTrue(json.contains("assistant"));
        assertTrue(json.contains("planner"));
    }

    @Test
    void testOnLoopEndMaxIteSendsSystem() {
        hook.onLoopEnd(true, "max iterations reached");
        String json = fakeSession.messages.get(0);
        assertTrue(json.contains("system"));
        assertTrue(json.contains("max iterations"));
    }

    @Test
    void testDoesNotSendWhenSessionClosed() {
        fakeSession.open = false;
        hook.onLoopStart("test");
        assertEquals(0, fakeSession.messages.size());
    }

    @Test
    void testOnThinkingSendsThinkingMessage() throws Exception {
        ObjectMapper om = new ObjectMapper();
        hook.onThinking("thought", "reasoning");
        String json = fakeSession.messages.get(0);
        com.coloop.agent.server.dto.WebSocketMessage msg = om.readValue(json, com.coloop.agent.server.dto.WebSocketMessage.class);
        assertEquals("planner", msg.getAgentName());
        assertEquals("thinking", msg.getType());
    }

    @Test
    void testOnUserMessageInjectedSendsUserMessage() throws Exception {
        ObjectMapper om = new ObjectMapper();
        hook.onUserMessageInjected("injected");
        String json = fakeSession.messages.get(0);
        com.coloop.agent.server.dto.WebSocketMessage msg = om.readValue(json, com.coloop.agent.server.dto.WebSocketMessage.class);
        assertEquals("planner", msg.getAgentName());
        assertEquals("user", msg.getType());
    }

    private static class FakeSession implements WebSocketSession {
        final List<String> messages = new CopyOnWriteArrayList<>();
        volatile boolean open = true;

        @Override public boolean isOpen() { return open; }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            if (message instanceof TextMessage tm) {
                messages.add(tm.getPayload());
            }
        }

        @Override public String getId() { return "fake"; }

        // --- Stub methods required by WebSocketSession interface ---
        @Override public URI getUri() { return null; }
        @Override public HttpHeaders getHandshakeHeaders() { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes() { return Collections.emptyMap(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int limit) {}
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int limit) {}
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return Collections.emptyList(); }
        @Override public void close() throws IOException {}
        @Override public void close(CloseStatus status) throws IOException {}
    }
}
