package com.coloop.agent.capability.task.command;

import com.coloop.agent.capability.task.TaskService;
import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;

import java.util.List;

public class TasksCommand implements Command {

    private final TaskService taskService;

    public TasksCommand(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "tasks";
    }

    @Override
    public String getDescription() {
        return "查看任务列表或单个任务详情";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.success(formatAllTasks());
        }

        String id = args.trim();
        Task task = taskService.get(id);
        if (task == null) {
            return CommandResult.success("Task not found: " + id);
        }
        return CommandResult.success(formatSingleTask(task));
    }

    private String formatAllTasks() {
        List<Task> tasks = taskService.list();
        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Tasks:\n");
        for (TaskStatus status : new TaskStatus[]{TaskStatus.IN_PROGRESS, TaskStatus.PENDING, TaskStatus.COMPLETED}) {
            List<Task> filtered = tasks.stream()
                    .filter(t -> t.getStatus() == status)
                    .toList();
            if (filtered.isEmpty()) continue;

            sb.append("\n").append(status).append(":\n");
            for (Task t : filtered) {
                sb.append(String.format("  %s %s\n", t.getId(), t.getSubject()));
            }
        }
        return sb.toString().trim();
    }

    private String formatSingleTask(Task task) {
        return String.format(
                "ID: %s\nSubject: %s\nDescription: %s\nStatus: %s\nBlockedBy: %s\nBlocks: %s",
                task.getId(), task.getSubject(), task.getDescription(),
                task.getStatus(), task.getBlockedBy(), task.getBlocks()
        );
    }
}
