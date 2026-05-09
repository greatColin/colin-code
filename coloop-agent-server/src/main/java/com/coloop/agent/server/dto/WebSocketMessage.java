package com.coloop.agent.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.coloop.agent.core.history.SessionMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    private String type;
    private Map<String, Object> payload;
    private long timestamp;
    private String agentName;

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

    public static WebSocketMessage newSession() {
        return new WebSocketMessage("new_session", new HashMap<>());
    }

    public static WebSocketMessage taskList(List<Map<String, Object>> tasks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tasks", tasks);
        return new WebSocketMessage("task_list", payload);
    }

    public static WebSocketMessage taskUpdate(int taskId, String status, String description) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", taskId);
        payload.put("status", status);
        payload.put("description", description);
        return new WebSocketMessage("task_update", payload);
    }

    public WebSocketMessage withAgent(String name) { this.agentName = name; return this; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public static WebSocketMessage subagentCreated(String name, String description, String summary) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("description", description);
        if (summary != null) payload.put("summary", summary);
        return new WebSocketMessage("subagent_created", payload);
    }

    public static WebSocketMessage subagentCleared(String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        return new WebSocketMessage("subagent_cleared", payload);
    }

    public static WebSocketMessage toast(String message, int durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("durationMs", durationMs);
        return new WebSocketMessage("toast", payload);
    }

    public static WebSocketMessage historyList(List<SessionMeta> sessions) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SessionMeta s : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.id);
            item.put("title", s.title);
            item.put("createdAt", s.createdAt);
            item.put("updatedAt", s.updatedAt);
            list.add(item);
        }
        payload.put("sessions", list);
        return new WebSocketMessage("history_list", payload);
    }

    public static WebSocketMessage sessionLoaded(String sessionId, String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("title", title);
        return new WebSocketMessage("session_loaded", payload);
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
