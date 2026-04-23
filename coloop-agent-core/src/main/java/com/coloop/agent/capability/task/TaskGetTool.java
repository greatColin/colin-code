package com.coloop.agent.capability.task;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.core.task.Task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskGetTool extends BaseTool {

    private final TaskService taskService;

    public TaskGetTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "task_get";
    }

    @Override
    public String getDescription() {
        return "获取单个任务的完整信息，包括依赖关系。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "string", "description", "任务ID"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("id"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String id = (String) params.get("id");
        Task task = taskService.get(id);
        if (task == null) {
            return "[Error: task not found: " + id + "]";
        }

        return String.format(
                "ID: %s\nSubject: %s\nDescription: %s\nStatus: %s\nBlockedBy: %s\nBlocks: %s",
                task.getId(),
                task.getSubject(),
                task.getDescription(),
                task.getStatus(),
                task.getBlockedBy(),
                task.getBlocks()
        );
    }
}
