package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.runtime.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListModelsTool extends BaseTool {

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public ListModelsTool(AppConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "ListModels";
    }

    @Override
    public String getDescription() {
        return "List all configured LLM models with their names, descriptions, and capabilities. " +
               "Use this to choose the right model when creating a subagent.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        params.put("required", List.of());
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (Map.Entry<String, AppConfig.ModelConfig> entry : config.getModels().entrySet()) {
            AppConfig.ModelConfig mc = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("key", entry.getKey());
            info.put("name", mc.getModel());
            info.put("description", mc.getDescription());
            info.put("maxContextSize", mc.hasMaxContextSize() ? mc.getMaxContextSize() : null);
            models.add(info);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultModel", config.getDefaultModel());
        result.put("models", models);

        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
