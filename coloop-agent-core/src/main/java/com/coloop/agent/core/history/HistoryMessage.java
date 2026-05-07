package com.coloop.agent.core.history;

/**
 * A single message in conversation history.
 * Mirrors the WebSocket message types rendered by the frontend.
 */
public class HistoryMessage {
    public String type;
    public String agent;
    public long timestamp;

    public String content;
    public String name;
    public String args;
    public String fullArgs;
    public String result;
    public Boolean success;
    public Integer attempt;
    public String description;
    public String message;
    public String reasoning;

    public HistoryMessage() {}

    public static HistoryMessage user(String agent, String content) {
        HistoryMessage m = new HistoryMessage();
        m.type = "user";
        m.agent = agent;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage assistant(String agent, String content) {
        HistoryMessage m = new HistoryMessage();
        m.type = "assistant";
        m.agent = agent;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage system(String agent, String message) {
        HistoryMessage m = new HistoryMessage();
        m.type = "system";
        m.agent = agent;
        m.message = message;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage thinking(String agent, String content, String reasoning) {
        HistoryMessage m = new HistoryMessage();
        m.type = "thinking";
        m.agent = agent;
        m.content = content;
        m.reasoning = reasoning;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage toolCall(String agent, String name, String args, String fullArgs) {
        HistoryMessage m = new HistoryMessage();
        m.type = "tool_call";
        m.agent = agent;
        m.name = name;
        m.args = args;
        m.fullArgs = fullArgs;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage toolResult(String agent, String name, String result, boolean success) {
        HistoryMessage m = new HistoryMessage();
        m.type = "tool_result";
        m.agent = agent;
        m.name = name;
        m.result = result;
        m.success = success;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage loopStart(String agent, int attempt) {
        HistoryMessage m = new HistoryMessage();
        m.type = "loop_start";
        m.agent = agent;
        m.attempt = attempt;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage subagentCreated(String agent, String description) {
        HistoryMessage m = new HistoryMessage();
        m.type = "subagent_created";
        m.agent = agent;
        m.description = description;
        m.timestamp = System.currentTimeMillis();
        return m;
    }
}
