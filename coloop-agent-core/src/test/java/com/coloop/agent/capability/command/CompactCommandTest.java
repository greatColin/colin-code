package com.coloop.agent.capability.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompactCommandTest {

    private final CompactCommand command = new CompactCommand();

    @Test
    void testGetName() {
        assertEquals("compact", command.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(command.getDescription());
    }

    @Test
    void testExecuteWithNullAgentLoop() {
        CommandContext ctx = new CommandContext(new AppConfig());
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        assertTrue(result.getMessage().contains("Context compacted"));
    }

    @Test
    void testExecuteCallsAgentLoopReset() {
        boolean[] resetCalled = {false};
        AgentLoop agentLoop = new AgentLoop(null, null, null, null, null, new AppConfig()) {
            @Override
            public void reset() {
                resetCalled[0] = true;
            }
        };

        CommandContext ctx = new CommandContext(new AppConfig());
        ctx.setAgentLoop(agentLoop);
        command.execute(ctx, "");

        assertTrue(resetCalled[0]);
    }
}
