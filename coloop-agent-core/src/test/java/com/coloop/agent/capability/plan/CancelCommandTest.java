package com.coloop.agent.capability.plan;

import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CancelCommandTest {

    @Test
    public void testClearsPendingPlan() {
        ConversationState state = new ConversationState();
        state.setPendingPlan("some plan");

        CancelCommand cmd = new CancelCommand(state);
        CommandResult result = cmd.execute(new CommandContext(new AppConfig()), "");

        assertNull(state.getPendingPlan());
        assertTrue(result.getMessage().contains("cancelled"));
    }

    @Test
    public void testHandlesNoPendingPlan() {
        ConversationState state = new ConversationState();

        CancelCommand cmd = new CancelCommand(state);
        CommandResult result = cmd.execute(new CommandContext(new AppConfig()), "");

        assertTrue(result.getMessage().contains("No pending plan"));
    }

    @Test
    public void testGetName() {
        assertEquals("cancel", new CancelCommand(new ConversationState()).getName());
    }
}
