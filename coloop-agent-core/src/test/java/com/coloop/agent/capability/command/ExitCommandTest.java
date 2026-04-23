package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExitCommandTest {

    private final ExitCommand command = new ExitCommand();

    @Test
    void testGetName() {
        assertEquals("exit", command.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(command.getDescription());
        assertFalse(command.getDescription().isEmpty());
    }

    @Test
    void testExecuteReturnsTerminateResult() {
        CommandContext ctx = new CommandContext(new AppConfig());
        CommandResult result = command.execute(ctx, "");

        assertTrue(result.shouldTerminate());
        assertEquals("Exited", result.getMessage());
    }

    @Test
    void testExecuteIgnoresArgs() {
        CommandContext ctx = new CommandContext(new AppConfig());
        CommandResult result = command.execute(ctx, "some args");

        assertTrue(result.shouldTerminate());
        assertEquals("Exited", result.getMessage());
    }
}
