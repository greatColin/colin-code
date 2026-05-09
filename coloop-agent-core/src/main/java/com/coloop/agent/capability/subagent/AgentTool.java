package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.tool.BaseTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class AgentTool extends BaseTool {

    private static final java.util.Set<String> FORBIDDEN_TOOLS = java.util.Set.of("Agent", "SendMessage");

    private final SubagentRegistry registry;
    private final SubagentLoopFactory factory;

    public AgentTool(SubagentRegistry registry, SubagentLoopFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    @Override
    public String getName() {
        return "Agent";
    }

    @Override
    public String getDescription() {
        return "Create a new subagent to handle a task. " +
               "IMPORTANT: To continue an existing conversation, use the 'SendMessage' tool. " +
               "Calling 'Agent' again with the same 'name' replaces the subagent and loses its history. " +
               "Only use 'Agent' when you need a fresh subagent or a different configuration.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", Map.of("type", "string", "description", "Subagent unique name; same name replaces old instance."));
        props.put("description", Map.of("type", "string", "description", "Short description shown in sidebar."));
        props.put("system_prompt", Map.of("type", "string", "description", "Subagent system prompt."));
        props.put("prompt", Map.of("type", "string", "description", "First user message, triggers one loop on creation."));
        props.put("tool_names", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Tool name whitelist; omit for default parent toolset minus Agent/SendMessage."
        ));
        props.put("return_thinking", Map.of(
            "type", "boolean",
            "description", "Whether to include <think> blocks in the result returned to the parent agent. Default false."
        ));
        props.put("model", Map.of(
            "type", "string",
            "description", "Model config key (e.g. 'minimax', 'glm-4-free'). Omit to use the main agent's model."
        ));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("name", "description", "system_prompt", "prompt"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        // Validate required fields
        String name = getStringParam(params, "name");
        if (name == null || name.isEmpty())
            return "Error: missing required field 'name'";
        String description = getStringParam(params, "description");
        if (description == null || description.isEmpty())
            return "Error: missing required field 'description'";
        String systemPrompt = getStringParam(params, "system_prompt");
        if (systemPrompt == null || systemPrompt.isEmpty())
            return "Error: missing required field 'system_prompt'";
        String prompt = getStringParam(params, "prompt");
        if (prompt == null || prompt.isEmpty())
            return "Error: missing required field 'prompt'";

        @SuppressWarnings("unchecked")
        List<String> rawToolNames = (List<String>) params.get("tool_names");
        List<String> toolNames = filterToolNames(rawToolNames);

        if (rawToolNames != null && toolNames.isEmpty()) {
            return "Error: tool_names resulted in empty toolset";
        }

        // Whether to include <think> blocks in the result returned to the parent agent.
        // Default false because reasoning content is usually noise for the parent;
        // WebSocket thinking events are still sent to the frontend regardless.
        boolean returnThinking = Boolean.TRUE.equals(params.get("return_thinking"));
        String modelKey = getStringParam(params, "model");

        try {
            AgentLoop subLoop = factory.create(name, systemPrompt, toolNames, modelKey);
            SubagentInstance instance = new SubagentInstance(name, description, systemPrompt, toolNames, subLoop, returnThinking);

            synchronized (instance.runLock) {
                instance.running = true;
                registry.createOrReplace(name, instance);
            }

            try {
                String result = subLoop.chat(prompt);
                return returnThinking ? result : stripThinkBlocks(result);
            } finally {
                synchronized (instance.runLock) {
                    instance.running = false;
                }
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val instanceof String ? (String) val : null;
    }

    static List<String> filterToolNames(List<String> names) {
        if (names == null) return null;
        return names.stream()
            .filter(n -> !FORBIDDEN_TOOLS.contains(n))
            .collect(Collectors.toList());
    }

    /**
     * Strip <think>...</think> blocks from content.
     * Used when return_thinking is false so the parent agent receives clean results.
     * WebSocket thinking events are unaffected.
     */
    static String stripThinkBlocks(String content) {
        if (content == null) return null;
        return content.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
    }
}
