package com.coloop.agent.capability.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MdPromptCommandTest {

    @Test
    void testRenderWithArguments() {
        MdPromptCommand cmd = new MdPromptCommand("fix", "Fix code", "Review $ARGUMENTS for bugs.");
        CommandContext ctx = new CommandContext(new AppConfig());

        CommandResult result = cmd.execute(ctx, "src/Main.java");

        assertFalse(result.shouldTerminate());
        assertEquals("Review src/Main.java for bugs.", result.getMessage());
    }

    @Test
    void testRenderWithNullArgs() {
        MdPromptCommand cmd = new MdPromptCommand("test", "Run tests", "Run $ARGUMENTS");
        CommandContext ctx = new CommandContext(new AppConfig());

        CommandResult result = cmd.execute(ctx, null);

        assertEquals("Run ", result.getMessage());
    }

    @Test
    void testRenderWithEmptyArgs() {
        MdPromptCommand cmd = new MdPromptCommand("test", "Run tests", "Run $ARGUMENTS");
        CommandContext ctx = new CommandContext(new AppConfig());

        CommandResult result = cmd.execute(ctx, "");

        assertEquals("Run ", result.getMessage());
    }

    @Test
    void testRenderWithoutArgumentsPlaceholder() {
        MdPromptCommand cmd = new MdPromptCommand("explain", "Explain code", "Explain this code.");
        CommandContext ctx = new CommandContext(new AppConfig());

        CommandResult result = cmd.execute(ctx, "some args");

        assertEquals("Explain this code.", result.getMessage());
    }

    @Test
    void testForwardsToAgentLoopWhenAvailable() {
        MdPromptCommand cmd = new MdPromptCommand("fix", "Fix code", "Review $ARGUMENTS.");

        AgentLoop loop = new AgentLoop(null, null, null, null, null, null) {
            @Override
            public String chat(String userMessage) {
                assertEquals("Review Main.java.", userMessage);
                return "Fixed 2 issues.";
            }
        };

        CommandContext ctx = new CommandContext(new AppConfig());
        ctx.setAgentLoop(loop);

        CommandResult result = cmd.execute(ctx, "Main.java");

        assertEquals("Fixed 2 issues.", result.getMessage());
    }

    @Test
    void testNameAndDescription() {
        MdPromptCommand cmd = new MdPromptCommand("my-cmd", "My description", "body");

        assertEquals("my-cmd", cmd.getName());
        assertEquals("My description", cmd.getDescription());
    }
}
