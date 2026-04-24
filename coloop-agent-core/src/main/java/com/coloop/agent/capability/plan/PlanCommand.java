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
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.config.AppConfig;

public class PlanCommand implements Command {

    private final LLMProvider provider;
    private final AppConfig config;

    public PlanCommand(LLMProvider provider, AppConfig config) {
        this.provider = provider;
        this.config = config;
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

        // Step 2: Generate plan through the read-only loop
        String plan = planLoop.chat(args);

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
    // Package-private for unit testing.
    void savePlan(CommandContext ctx, String request, String plan) {
        AgentLoop mainLoop = ctx.getAgentLoop();
        if (mainLoop == null) {
            return;
        }
        ConversationState state = mainLoop.getConversationState();
        if (state == null) {
            return;
        }
        state.setPendingPlan(plan);
        state.setPlanRequest(request);
    }

    // Build the result message shown to the user after plan generation.
    // Package-private for unit testing.
    CommandResult buildResult(String plan) {
        String message = plan + "\n\n"
            + "Execute this plan? Reply with any message to confirm, or /cancel to abort.";
        return CommandResult.success(message);
    }
}
