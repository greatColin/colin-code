package com.coloop.agent.capability.task;

import com.coloop.agent.capability.task.command.TasksCommand;
import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.List;

public class TaskManagementCapability {

    private final TaskService taskService;
    private final TaskCreateTool taskCreateTool;
    private final TaskListTool taskListTool;
    private final TaskGetTool taskGetTool;
    private final TaskUpdateTool taskUpdateTool;
    private final TaskStatusPromptPlugin promptPlugin;
    private final TaskDisplayHook displayHook;
    private final TasksCommand tasksCommand;

    public TaskManagementCapability(AppConfig config) {
        this.taskService = new TaskService(new InMemoryTaskStore());
        this.taskCreateTool = new TaskCreateTool(taskService);
        this.taskListTool = new TaskListTool(taskService);
        this.taskGetTool = new TaskGetTool(taskService);
        this.taskUpdateTool = new TaskUpdateTool(taskService);
        this.promptPlugin = new TaskStatusPromptPlugin(taskService);
        this.displayHook = new TaskDisplayHook(taskService);
        this.tasksCommand = new TasksCommand(taskService);
    }

    public List<Tool> getTools() {
        return List.of(taskCreateTool, taskListTool, taskGetTool, taskUpdateTool);
    }

    public PromptPlugin getPromptPlugin() {
        return promptPlugin;
    }

    public AgentHook getHook() {
        return displayHook;
    }

    public TasksCommand getTasksCommand() {
        return tasksCommand;
    }

    public TaskService getTaskService() {
        return taskService;
    }
}
