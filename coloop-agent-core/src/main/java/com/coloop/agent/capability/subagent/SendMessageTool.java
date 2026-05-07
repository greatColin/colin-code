package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.tool.BaseTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SendMessageTool extends BaseTool {

    private final SubagentRegistry registry;

    public SendMessageTool(SubagentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "SendMessage";
    }

    @Override
    public String getDescription() {
        return "Send a follow-up message to an existing named subagent. " +
               "This preserves conversation history and context. " +
               "ALWAYS use this instead of re-creating the subagent with 'Agent' when the task has continuity. " +
               "The subagent must have been created via the '/Agent' tool first.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("to", Map.of("type", "string", "description", "Name of the target subagent."));
        props.put("message", Map.of("type", "string", "description", "The user message to append."));
        props.put("summary", Map.of("type", "string", "description", "5-10 word preview (v1: WS-event only, not rendered)."));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("to", "message"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String to = getStringParam(params, "to");
        if (to == null || to.isEmpty())
            return "Error: missing required field 'to'";
        String message = getStringParam(params, "message");
        if (message == null || message.isEmpty())
            return "Error: missing required field 'message'";
        // summary is optional and accepted but not yet used by renderer

        SubagentInstance inst = registry.get(to);
        if (inst == null) {
            return "Error: subagent '" + to + "' not found";
        }

        synchronized (inst.runLock) {
            if (inst.running) {
                // inject while running; agent will pick it up in next iteration
                inst.agentLoop.injectUserMessage(message);
                return "Message queued. Agent is currently processing.";
            }
            inst.running = true;
        }

        try {
            return inst.agentLoop.chat(message);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            synchronized (inst.runLock) {
                inst.running = false;
            }
        }
    }

    private static String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val instanceof String ? (String) val : null;
    }
}
