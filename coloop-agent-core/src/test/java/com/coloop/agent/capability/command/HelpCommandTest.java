package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelpCommandTest {

    @Test
    void testGetName() {
        CommandRegistry registry = new CommandRegistry();
        HelpCommand command = new HelpCommand(registry);
        assertEquals("help", command.getName());
    }

    @Test
    void testGetDescription() {
        CommandRegistry registry = new CommandRegistry();
        HelpCommand command = new HelpCommand(registry);
        assertNotNull(command.getDescription());
    }

    @Test
    void testExecuteWithEmptyRegistry() {
        CommandRegistry registry = new CommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        CommandContext ctx = new CommandContext(new AppConfig());
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        assertTrue(result.getMessage().contains("Available commands:"));
    }

    @Test
    void testExecuteListsAllCommands() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new SimpleCommand("cmd1", "First command"));
        registry.register(new SimpleCommand("cmd2", "Second command"));

        HelpCommand command = new HelpCommand(registry);
        CommandContext ctx = new CommandContext(new AppConfig());
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        String msg = result.getMessage();
        assertTrue(msg.contains("/cmd1"));
        assertTrue(msg.contains("First command"));
        assertTrue(msg.contains("/cmd2"));
        assertTrue(msg.contains("Second command"));
    }

    private static class SimpleCommand implements Command {
        private final String name;
        private final String description;

        SimpleCommand(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public CommandResult execute(CommandContext ctx, String args) {
            return CommandResult.success("ok");
        }
    }
}
