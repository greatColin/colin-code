package com.coloop.agent.capability.task;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskListTool extends BaseTool {

    private final TaskService taskService;

    public TaskListTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "task_list";
    }

    @Override
    public String getDescription() {
        return "列出所有任务及其当前状态。通常不需要主动调用，因为系统提示词已包含当前任务列表。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        List<Task> tasks = taskService.list();
        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        StringBuilder sb = new StringBuilder();
        for (TaskStatus status : new TaskStatus[]{TaskStatus.IN_PROGRESS, TaskStatus.PENDING, TaskStatus.COMPLETED}) {
            List<Task> filtered = tasks.stream()
                    .filter(t -> t.getStatus() == status)
                    .toList();
            if (filtered.isEmpty()) continue;

            sb.append("\n").append(status).append(":\n");
            for (Task t : filtered) {
                sb.append(String.format("  - %s %s\n", t.getId(), t.getSubject()));
            }
        }
        return sb.toString().trim();
    }
}
