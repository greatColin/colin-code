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
        List<Task> tasks = taskService.list();
        List<Task> active = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DELETED)
                .toList();

        if (active.isEmpty()) {
            return;
        }

        long inProgressCount = active.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long pendingCount = active.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long completedCount = active.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();

        StringBuilder sb = new StringBuilder();
        sb.append(AnsiColors.colorize("[TASKS] ", AnsiColors.FG_CYAN));
        sb.append(AnsiColors.colorize(String.valueOf(active.size()), AnsiColors.FG_WHITE));
        sb.append(" active (");
        sb.append(AnsiColors.colorize(String.valueOf(inProgressCount), AnsiColors.FG_YELLOW));
        sb.append(" in_progress, ");
        sb.append(AnsiColors.colorize(String.valueOf(pendingCount), AnsiColors.FG_WHITE));
        sb.append(" pending");
        if (completedCount > 0) {
            sb.append(", ");
            sb.append(AnsiColors.colorize(String.valueOf(completedCount), AnsiColors.FG_GREEN));
            sb.append(" completed");
        }
        sb.append(")");
        System.out.println(sb);

        for (Task task : active) {
            String icon;
            String color;
            switch (task.getStatus()) {
                case IN_PROGRESS -> {
                    icon = "⏳";
                    color = AnsiColors.FG_YELLOW;
                }
                case PENDING -> {
                    icon = "⏸";
                    color = AnsiColors.FG_WHITE;
                }
                case COMPLETED -> {
                    icon = "✅";
                    color = AnsiColors.FG_GREEN;
                }
                default -> {
                    icon = "?";
                    color = AnsiColors.FG_WHITE;
                }
            }
            String line = "  " + icon + " "
                    + task.getId() + "  "
                    + AnsiColors.colorize(task.getSubject(), color);
            System.out.println(line);
        }
    }
}
