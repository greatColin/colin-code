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
        // ================================================================
        // 任务管理提示词 —— 模仿 Claude Code 的 TodoWrite 行为契约
        // 目标：强制 LLM 在多步骤任务前先规划、再执行、过程中更新进度
        // ================================================================
        StringBuilder sb = new StringBuilder();

        // 标题段：明确告知 LLM 它拥有任务管理工具
        sb.append("## Task Management\n\n");
        // 工具清单：让 LLM 知道有哪些工具可用
        sb.append("You have access to task_create, task_update, task_get, and task_list tools.\n");

        // 强制规则段（核心）：用 MUST / BEFORE / VERY frequently 等强语气词
        // 规则1：任何多步骤/多文件/涉及工具或研究的请求，必须先创建任务列表
        // 规则2：把复杂工作拆成小的、具体的任务（每个文件或步骤一个任务）
        // 规则3：每完成一步，用 task_update 把状态改为 COMPLETED
        // 规则4：同时只能有一个 IN_PROGRESS 任务（已有逻辑自动完成旧的）
        // 规则5：用 blocked_by 表达步骤之间的依赖关系
        // 规则6：强调频繁使用，先规划再执行能避免错误
        sb.append("MANDATORY RULES — you MUST follow these:\n");
        sb.append("1. For ANY request involving multiple steps, files, tools, or research, ");
        sb.append("   create a task list using task_create BEFORE taking any action.\n");
        sb.append("2. Break complex work into small, concrete tasks (one task per file or step).\n");
        sb.append("3. After completing each step, update the task status to COMPLETED via task_update.\n");
        sb.append("4. Only ONE task may be IN_PROGRESS at a time.\n");
        sb.append("5. Use blocked_by to express dependencies between steps.\n");
        sb.append("6. You MUST use these tools VERY frequently. Planning first prevents mistakes.\n");
        sb.append("7. NEVER list tasks in plain text — ALWAYS use task_create tool to create them.\n");
        sb.append("8. The user can see your task progress in a sidebar — keep it updated.\n\n");

        // 动态状态段：注入当前任务列表，让 LLM 知道进度
        // 没任务时也要显示提示，提醒 LLM 该创建任务了
        List<Task> tasks = taskService.list();
        List<Task> active = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.PENDING)
                .toList();
        long completedCount = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();

        if (tasks.isEmpty()) {
            // 无任务状态：提示 LLM 遇到多步骤工作时应该创建任务
            sb.append("Current status: No active tasks. Create tasks for any multi-step work.\n");
        } else {
            // 有任务时：列出进行中和待办的任务，已完成的按阈值折叠
            sb.append("## Current Tasks\n\n");
            for (Task t : active) {
                sb.append(String.format("- [%s] %s %s\n", t.getStatus(), t.getId(), t.getSubject()));
            }
            if (completedCount > 0) {
                if (completedCount <= COMPLETED_FOLD_THRESHOLD) {
                    // 已完成任务少时全部列出
                    tasks.stream()
                            .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                            .forEach(t -> sb.append(String.format("- [COMPLETED] %s %s\n", t.getId(), t.getSubject())));
                } else {
                    // 已完成任务多时折叠，节省 token
                    sb.append(String.format("...and %d completed task(s)\n", completedCount));
                }
            }
        }
        return sb.toString().trim();
    }
}
