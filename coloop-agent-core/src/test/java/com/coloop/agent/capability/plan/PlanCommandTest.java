package com.coloop.agent.capability.plan;

import com.coloop.agent.capability.provider.mock.MockProvider;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlanCommandTest {

    @Test
    public void testGetNameAndDescription() {
        PlanCommand cmd = new PlanCommand(null, new AppConfig());
        assertEquals("plan", cmd.getName());
        assertNotNull(cmd.getDescription());
        assertFalse(cmd.getDescription().isEmpty());
    }

    @Test
    public void testBuildResultContainsPlanAndPrompt() {
        PlanCommand cmd = new PlanCommand(null, new AppConfig());
        CommandResult result = cmd.buildResult("step 1: do something");

        assertTrue(result.getMessage().contains("step 1: do something"));
        assertTrue(result.getMessage().contains("Execute this plan?"));
        assertTrue(result.getMessage().contains("/cancel"));
    }

    @Test
    public void testSavePlanStoresInConversationState() {
        AppConfig config = new AppConfig();
        MockProvider provider = new MockProvider(List.of(response("mock plan")));

        com.coloop.agent.core.agent.AgentLoop mainLoop =
            new CapabilityLoader()
                .withCapability(com.coloop.agent.runtime.StandardCapability.BASE_PROMPT, config)
                .build(provider, config);

        CommandContext ctx = new CommandContext(config);
        ctx.setAgentLoop(mainLoop);

        PlanCommand cmd = new PlanCommand(null, config);
        cmd.savePlan(ctx, "original request", "the plan");

        ConversationState state = mainLoop.getConversationState();
        assertEquals("the plan", state.getPendingPlan());
        assertEquals("original request", state.getPlanRequest());
    }

    @Test
    public void testSavePlanHandlesNullAgentLoop() {
        PlanCommand cmd = new PlanCommand(null, new AppConfig());
        CommandContext ctx = new CommandContext(new AppConfig());
        assertDoesNotThrow(() -> cmd.savePlan(ctx, "req", "plan"));
    }

    private static LLMResponse response(String content) {
        LLMResponse r = new LLMResponse();
        r.setContent(content);
        return r;
    }
}
