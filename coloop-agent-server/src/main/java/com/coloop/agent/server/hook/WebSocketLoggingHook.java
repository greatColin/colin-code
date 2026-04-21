package com.coloop.agent.server.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将 AgentLoop 生命周期事件转为 JSON 通过 WebSocket 推送到前端。
 */
public class WebSocketLoggingHook implements AgentHook {

    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final AtomicInteger loopCount;

    public WebSocketLoggingHook(WebSocketSession session) {
        this.session = session;
        this.objectMapper = new ObjectMapper();
        this.loopCount = new AtomicInteger(0);
    }

    @Override
    public void onLoopStart(String userMessage) {
        loopCount.set(0);
        send(WebSocketMessage.user(userMessage));
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        int current = loopCount.incrementAndGet();
        send(WebSocketMessage.loopStart(current));
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
        } catch (IOException e) {
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
    }

    @Override
    public void onUserMessageInjected(String message) {
        send(WebSocketMessage.user(message));
    }

    private void send(WebSocketMessage msg) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            System.err.println("WebSocket send failed: " + e.getMessage());
        }
    }
}
