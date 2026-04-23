package com.coloop.agent.core.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    void testRegisterAndGet() {
        Command cmd = new SimpleCommand("test", "A test command");
        registry.register(cmd);

        assertSame(cmd, registry.get("test"));
    }

    @Test
    void testGetNotFound() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void testHasCommand() {
        registry.register(new SimpleCommand("exists", ""));
        assertTrue(registry.hasCommand("exists"));
        assertFalse(registry.hasCommand("missing"));
    }

    @Test
    void testGetAllPreservesOrder() {
        Command cmd1 = new SimpleCommand("first", "First cmd");
        Command cmd2 = new SimpleCommand("second", "Second cmd");
        Command cmd3 = new SimpleCommand("third", "Third cmd");

        registry.register(cmd1);
        registry.register(cmd2);
        registry.register(cmd3);

        List<Command> all = registry.getAll();
        assertEquals(3, all.size());
        assertEquals("first", all.get(0).getName());
        assertEquals("second", all.get(1).getName());
        assertEquals("third", all.get(2).getName());
    }

    @Test
    void testGetAllReturnsNewList() {
        registry.register(new SimpleCommand("cmd", ""));
        List<Command> all1 = registry.getAll();
        List<Command> all2 = registry.getAll();
        assertNotSame(all1, all2);
    }

    @Test
    void testRegisterOverwrite() {
        Command original = new SimpleCommand("dup", "Original");
        Command replacement = new SimpleCommand("dup", "Replacement");

        registry.register(original);
        registry.register(replacement);

        assertSame(replacement, registry.get("dup"));
        assertEquals("Replacement", registry.get("dup").getDescription());
    }

    @Test
    void testGetAllEmptyRegistry() {
        List<Command> all = registry.getAll();
        assertTrue(all.isEmpty());
    }

    /** 简单的命令实现，仅用于测试注册表行为 */
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
