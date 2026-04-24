package com.coloop.agent.capability.task;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskUpdateTool extends BaseTool {

    private final TaskService taskService;

    public TaskUpdateTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "task_update";
    }

    @Override
    public String getDescription() {
        return "更新任务状态或信息。将任务设为 IN_PROGRESS 时，系统会自动完成之前进行中的任务。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "string"));
        props.put("subject", Map.of("type", "string"));
        props.put("description", Map.of("type", "string"));
        props.put("status", Map.of("type", "string", "enum", List.of("PENDING", "IN_PROGRESS", "COMPLETED", "DELETED")));
        props.put("blocked_by", Map.of("type", "array", "items", Map.of("type", "string")));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("id"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String id = (String) params.get("id");
        String subject = (String) params.get("subject");
        String description = (String) params.get("description");
        String statusStr = (String) params.get("status");
        @SuppressWarnings("unchecked")
        List<String> blockedBy = (List<String>) params.get("blocked_by");

        TaskStatus status = null;
        if (statusStr != null) {
            try {
                status = TaskStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                return "[Error: invalid status: " + statusStr
                        + ". Valid values: PENDING, IN_PROGRESS, COMPLETED, DELETED]";
            }
        }

        Task task = taskService.update(id, subject, description, status, blockedBy);
        if (task == null) {
            return "[Error: task not found: " + id + "]";
        }

        if (status != null) {
            return String.format("Updated task %s: status=%s", task.getId(), task.getStatus());
        }
        return String.format("Updated task %s", task.getId());
    }
}
