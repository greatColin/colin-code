package com.coloop.agent.server.hook;

import com.coloop.agent.capability.task.TaskService;
import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.context.PlanTask;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将 AgentLoop 生命周期事件转为 JSON 通过 WebSocket 推送到前端。
 */
public class WebSocketLoggingHook implements AgentHook {

    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final AtomicInteger loopCount;
    private AgentLoop agentLoop;
    private TaskService taskService;

    public WebSocketLoggingHook(WebSocketSession session, AgentLoop agentLoop) {
        this.session = session;
        this.objectMapper = new ObjectMapper();
        this.loopCount = new AtomicInteger(0);
        this.agentLoop = agentLoop;
    }

    public WebSocketLoggingHook(WebSocketSession session) {
        this(session, null);
    }

    public void setAgentLoop(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void onLoopStart(String userMessage) {
        loopCount.set(0);
        send(WebSocketMessage.user(userMessage));
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        int current = loopCount.incrementAndGet();
        send(WebSocketMessage.loopStart(current));
        if (agentLoop != null) {
            send(WebSocketMessage.contextUsage(
                agentLoop.getCurrentTokenCount(),
                agentLoop.getContextLimit(),
                agentLoop.getContextUsagePercent()
            ));
        }
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        send(WebSocketMessage.thinking(content, reasoningContent));
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        try {
            String fullArgs = objectMapper.writeValueAsString(toolCall.getArguments());
            send(WebSocketMessage.toolCall(toolCall.getName(), formattedArgs, fullArgs));
        } catch (IOException e) {
            System.err.println("Failed to serialize tool arguments: " + e.getMessage());
            send(WebSocketMessage.toolCall(toolCall.getName(), formattedArgs, "{}"));
        }
        boolean success = result != null && !result.startsWith("Error:");
        send(WebSocketMessage.toolResult(toolCall.getName(), result, success));
        advancePlanTaskProgress();

        // 当 task 相关工具被调用时，推送最新任务列表到前端
        if (toolCall.getName().startsWith("task_")) {
            System.out.println("[TaskSidebar] Detected task tool call: " + toolCall.getName());
            sendTaskListFromTaskService();
        }
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if (maxIte) {
            send(WebSocketMessage.system(finalResponse));
        } else {
            send(WebSocketMessage.assistant(finalResponse));
        }
        // 消息历史已更新（addAssistantMessage 在 onLoopEnd 前执行），同步最新上下文占用
        if (agentLoop != null) {
            send(WebSocketMessage.contextUsage(
                agentLoop.getCurrentTokenCount(),
                agentLoop.getContextLimit(),
                agentLoop.getContextUsagePercent()
            ));
        }
        completePlanTasks();
    }

    @Override
    public void onUserMessageInjected(String message) {
        send(WebSocketMessage.user(message));
    }

    @Override
    public void onStreamChunk(String chunk) {
        send(WebSocketMessage.streamChunk(chunk));
    }

    private void send(WebSocketMessage msg) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("WebSocket send failed: " + e.getMessage());
        }
    }

    /** 推进计划任务进度：当前 in_progress 标记为 completed，下一个 pending 标记为 in_progress。 */
    private void advancePlanTaskProgress() {
        if (agentLoop == null) return;
        ConversationState state = agentLoop.getConversationState();
        if (state == null) return;
        List<PlanTask> tasks = state.getPlanTasks();
        if (tasks == null || tasks.isEmpty()) return;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getStatus() == PlanTask.Status.IN_PROGRESS) {
                tasks.get(i).setStatus(PlanTask.Status.COMPLETED);
                sendTaskUpdate(tasks.get(i));
                if (i + 1 < tasks.size()) {
                    tasks.get(i + 1).setStatus(PlanTask.Status.IN_PROGRESS);
                    sendTaskUpdate(tasks.get(i + 1));
                }
                return;
            }
        }

        // 尚无 in_progress 任务，将第一个 pending 标记为 in_progress
        for (PlanTask task : tasks) {
            if (task.getStatus() == PlanTask.Status.PENDING) {
                task.setStatus(PlanTask.Status.IN_PROGRESS);
                sendTaskUpdate(task);
                return;
            }
        }
    }

    /** 计划执行完毕，将所有未完成任务标记为 completed 并清除任务列表。 */
    private void completePlanTasks() {
        if (agentLoop == null) return;
        ConversationState state = agentLoop.getConversationState();
        if (state == null) return;
        List<PlanTask> tasks = state.getPlanTasks();
        if (tasks == null || tasks.isEmpty()) return;

        for (PlanTask task : tasks) {
            if (task.getStatus() != PlanTask.Status.COMPLETED) {
                task.setStatus(PlanTask.Status.COMPLETED);
                sendTaskUpdate(task);
            }
        }
        state.setPlanTasks(null);
    }

    private void sendTaskUpdate(PlanTask task) {
        send(WebSocketMessage.taskUpdate(
            task.getId(),
            task.getStatus().name().toLowerCase(),
            task.getDescription()
        ));
    }

    /** 从 TaskService 获取任务列表并推送到前端（通用任务系统）。 */
    private void sendTaskListFromTaskService() {
        if (taskService == null) {
            System.err.println("[TaskSidebar] taskService is null, skipping task list push");
            return;
        }
        List<Task> tasks = taskService.list();
        System.out.println("[TaskSidebar] Pushing " + (tasks != null ? tasks.size() : 0) + " tasks to frontend");
        if (tasks == null || tasks.isEmpty()) return;

        List<Map<String, Object>> payload = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.DELETED) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("description", task.getSubject());
            item.put("status", task.getStatus().name().toLowerCase());
            payload.add(item);
        }
        send(WebSocketMessage.taskList(payload));
    }
}
