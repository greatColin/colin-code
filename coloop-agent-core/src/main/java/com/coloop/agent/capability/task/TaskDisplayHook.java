package com.coloop.agent.capability.task;

import com.coloop.agent.capability.hook.AnsiColors;
import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;

import java.util.List;

public class TaskDisplayHook implements AgentHook {

    private final TaskService taskService;

    public TaskDisplayHook(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        if (!toolCall.getName().startsWith("task_")) {
            return;
        }

        List<Task> active = taskService.list().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.PENDING)
                .toList();

        if (active.isEmpty()) {
            return;
        }

        Task inProgress = active.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);

        if (inProgress != null) {
            String line = AnsiColors.label("TASK", AnsiColors.TASK_COLOR,
                    inProgress.getId() + " " + inProgress.getSubject() + " -> IN_PROGRESS",
                    AnsiColors.FG_WHITE);
            System.out.println(line);
        }
    }
}
