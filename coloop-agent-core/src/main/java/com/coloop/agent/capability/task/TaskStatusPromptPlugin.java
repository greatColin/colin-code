package com.coloop.agent.capability.task;

import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.List;
import java.util.Map;

public class TaskStatusPromptPlugin implements PromptPlugin {

    private static final int COMPLETED_FOLD_THRESHOLD = 3;

    private final TaskService taskService;

    public TaskStatusPromptPlugin(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "task_status";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String generate(AppConfig config, Map<String, Object> runtimeContext) {
        List<Task> tasks = taskService.list();
        if (tasks.isEmpty()) {
            return null;
        }

        List<Task> active = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.PENDING)
                .toList();

        long completedCount = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();

        if (active.isEmpty() && completedCount == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 任务管理\n\n");
        sb.append("你拥有 task_create / task_update / task_get / task_list 工具来跟踪多步骤工作。\n");
        sb.append("规则：\n");
        sb.append("1. 复杂请求主动创建任务\n");
        sb.append("2. 同时只能有一个 IN_PROGRESS 任务\n");
        sb.append("3. 用 blocked_by 表达步骤依赖\n\n");
        sb.append("## 当前任务\n\n");

        for (Task t : active) {
            sb.append(String.format("- [%s] %s %s\n", t.getStatus(), t.getId(), t.getSubject()));
        }

        if (completedCount > 0) {
            if (completedCount <= COMPLETED_FOLD_THRESHOLD) {
                tasks.stream()
                        .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                        .forEach(t -> sb.append(String.format("- [COMPLETED] %s %s\n", t.getId(), t.getSubject())));
            } else {
                sb.append(String.format("...及 %d 个已完成任务\n", completedCount));
            }
        }

        return sb.toString().trim();
    }
}
