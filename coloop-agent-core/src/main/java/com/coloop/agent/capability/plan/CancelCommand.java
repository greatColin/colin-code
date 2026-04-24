package com.coloop.agent.capability.plan;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.context.ConversationState;

public class CancelCommand implements Command {

    private final ConversationState conversationState;

    public CancelCommand(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    @Override
    public String getName() {
        return "cancel";
    }

    @Override
    public String getDescription() {
        return "Cancel the pending plan.";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        if (conversationState.getPendingPlan() != null) {
            conversationState.setPendingPlan(null);
            conversationState.setPlanRequest(null);
            return CommandResult.success("Plan cancelled.");
        }
        return CommandResult.success("No pending plan to cancel.");
    }
}
