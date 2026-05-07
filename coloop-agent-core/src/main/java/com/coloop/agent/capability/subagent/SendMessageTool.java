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
        props.put("return_thinking", Map.of(
            "type", "boolean",
            "description", "Whether to include <think> blocks in the result returned to the parent agent. Default false."
        ));

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

        // Whether to include <think> blocks in the result returned to the parent agent.
        // Falls back to the subagent's creation-time setting if not explicitly provided.
        // Default false because reasoning content is usually noise for the parent;
        // WebSocket thinking events are still sent to the frontend regardless.
        boolean returnThinking;
        if (params.containsKey("return_thinking")) {
            returnThinking = Boolean.TRUE.equals(params.get("return_thinking"));
        } else {
            returnThinking = inst.returnThinking;
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
            String result = inst.agentLoop.chat(message);
            return returnThinking ? result : AgentTool.stripThinkBlocks(result);
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
