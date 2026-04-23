package com.coloop.agent.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    private String type;
    private Map<String, Object> payload;
    private long timestamp;

    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public WebSocketMessage(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public static WebSocketMessage user(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        return new WebSocketMessage("user", payload);
    }

    public static WebSocketMessage loopStart(int attempt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attempt", attempt);
        return new WebSocketMessage("loop_start", payload);
    }

    public static WebSocketMessage thinking(String content, String reasoning) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("reasoning", reasoning);
        return new WebSocketMessage("thinking", payload);
    }

    public static WebSocketMessage toolCall(String name, String args, String fullArgs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("args", args);
        payload.put("fullArgs", fullArgs);
        return new WebSocketMessage("tool_call", payload);
    }

    public static WebSocketMessage toolResult(String name, String result, boolean success) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("result", result);
        payload.put("success", success);
        return new WebSocketMessage("tool_result", payload);
    }

    public static WebSocketMessage streamChunk(String chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", chunk);
        return new WebSocketMessage("stream_chunk", payload);
    }

    public static WebSocketMessage assistant(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        return new WebSocketMessage("assistant", payload);
    }

    public static WebSocketMessage system(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return new WebSocketMessage("system", payload);
    }

    public static WebSocketMessage error(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return new WebSocketMessage("error", payload);
    }

    public static WebSocketMessage commands(List<Map<String, String>> commands) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commands", commands);
        return new WebSocketMessage("commands", payload);
    }

    public static WebSocketMessage contextUsage(int tokens, int limit, int percent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tokens", tokens);
        payload.put("limit", limit);
        payload.put("percent", percent);
        return new WebSocketMessage("context_usage", payload);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
