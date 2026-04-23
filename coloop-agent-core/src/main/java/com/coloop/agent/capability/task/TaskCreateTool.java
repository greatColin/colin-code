package com.coloop.agent.capability.task;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.core.task.Task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskCreateTool extends BaseTool {

    private final TaskService taskService;

    public TaskCreateTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "task_create";
    }

    @Override
    public String getDescription() {
        return "创建新任务。当用户请求涉及多个文件或步骤时，主动创建任务列表来跟踪进度。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subject", Map.of("type", "string", "description", "任务标题，用祈使句"));
        props.put("description", Map.of("type", "string", "description", "任务详细描述"));
        props.put("blocked_by", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "依赖的任务ID列表"
        ));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("subject"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String subject = (String) params.get("subject");
        String description = (String) params.getOrDefault("description", "");
        @SuppressWarnings("unchecked")
        List<String> blockedBy = (List<String>) params.get("blocked_by");

        Task task = taskService.create(subject, description);
        if (blockedBy != null && !blockedBy.isEmpty()) {
            taskService.update(task.getId(), null, null, null, blockedBy);
        }

        return String.format("Created task %s: %s [%s]",
                task.getId(), task.getSubject(), task.getStatus());
    }
}
