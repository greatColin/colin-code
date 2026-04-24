package com.coloop.agent.capability.plan;

import com.coloop.agent.capability.prompt.BasePromptPlugin;
import com.coloop.agent.capability.tool.exec.ExecTool;
import com.coloop.agent.capability.tool.filesystem.ListDirectoryTool;
import com.coloop.agent.capability.tool.filesystem.ReadFileTool;
import com.coloop.agent.capability.tool.filesystem.SearchFilesTool;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.context.PlanTask;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlanCommand implements Command {

    private final LLMProvider provider;
    private final AppConfig config;
    private final ConversationState sharedState;

    public PlanCommand(LLMProvider provider, AppConfig config, ConversationState sharedState) {
        this.provider = provider;
        this.config = config;
        this.sharedState = sharedState;
    }

    @Override
    public String getName() {
        return "plan";
    }

    @Override
    public String getDescription() {
        return "Enter plan mode: analyze the codebase and draft an execution plan before making changes.";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        // Step 1: Build isolated Plan Loop with read-only tools
        AgentLoop planLoop = buildPlanLoop();

        // Step 2: Generate plan through the read-only loop with streaming
        StringBuilder planBuilder = new StringBuilder();
        Consumer<String> chunkSender = ctx.getAttribute("streamChunkSender");

        planLoop.chatStream(args, new LLMProvider.StreamConsumer() {
            @Override
            public void onContent(String chunk) {
                planBuilder.append(chunk);
                if (chunkSender != null) {
                    chunkSender.accept(chunk);
                } else {
                    System.out.print(chunk);
                }
            }

            @Override
            public void onToolCall(ToolCallRequest toolCall) {}

            @Override
            public void onComplete(LLMResponse response) {}

            @Override
            public void onError(String error) {}
        });

        String plan = planBuilder.toString();

        // Step 3: Persist plan to shared conversation state
        savePlan(ctx, args, plan);

        // Step 4: Build confirmation prompt for the user
        return buildResult(plan);
    }

    // Build an isolated AgentLoop that only has read/exploratory tools.
    // Package-private for unit testing.
    AgentLoop buildPlanLoop() {
        return new CapabilityLoader()
            .withPromptPlugin(new BasePromptPlugin())
            .withPromptPlugin(new PlanPromptPlugin())
            .withTool(new ReadFileTool())
            .withTool(new SearchFilesTool())
            .withTool(new ListDirectoryTool())
            .withTool(new ExecTool(config.getExecTimeoutSeconds()))
            .build(provider, config);
    }

    // Save the generated plan and original request into shared state.
    // Also parses the plan text into structured tasks and sends them via WebSocket.
    // Package-private for unit testing.
    void savePlan(CommandContext ctx, String request, String plan) {
        if (sharedState != null) {
            sharedState.setPendingPlan(plan);
            sharedState.setPlanRequest(request);
        }
        List<PlanTask> tasks = parsePlanTasks(plan);
        if (sharedState != null) {
            sharedState.setPlanTasks(tasks);
        }
        sendTaskList(ctx, tasks);
    }

    // Extract numbered steps from plan text into structured PlanTask list.
    // Package-private for unit testing.
    List<PlanTask> parsePlanTasks(String plan) {
        List<PlanTask> tasks = new ArrayList<>();
        if (plan == null || plan.isEmpty()) {
            return tasks;
        }
        Pattern stepPattern = Pattern.compile("^\\s*(\\d+)[\\.\\)\\-]\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = stepPattern.matcher(plan);
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2).trim();
            tasks.add(new PlanTask(id, description));
        }
        return tasks;
    }

    // Send task list to frontend via WebSocket if sender is available.
    @SuppressWarnings("unchecked")
    private void sendTaskList(CommandContext ctx, List<PlanTask> tasks) {
        Consumer<List<Map<String, Object>>> sender = ctx.getAttribute("sendTaskList");
        if (sender == null || tasks.isEmpty()) {
            return;
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (PlanTask task : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("description", task.getDescription());
            item.put("status", task.getStatus().name().toLowerCase());
            payload.add(item);
        }
        sender.accept(payload);
    }

    // Build the result message shown to the user after plan generation.
    // Package-private for unit testing.
    CommandResult buildResult(String plan) {
        String message = plan + "\n\n"
            + "Execute this plan? Reply with any message to confirm, or /cancel to abort.";
        return CommandResult.success(message);
    }
}
