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
        PlanCommand cmd = new PlanCommand(null, new AppConfig(), new ConversationState());
        assertEquals("plan", cmd.getName());
        assertNotNull(cmd.getDescription());
        assertFalse(cmd.getDescription().isEmpty());
    }

    @Test
    public void testBuildResultContainsPlanAndPrompt() {
        PlanCommand cmd = new PlanCommand(null, new AppConfig(), new ConversationState());
        CommandResult result = cmd.buildResult("step 1: do something");

        assertTrue(result.getMessage().contains("step 1: do something"));
        assertTrue(result.getMessage().contains("Execute this plan?"));
        assertTrue(result.getMessage().contains("/cancel"));
    }

    @Test
    public void testSavePlanStoresInConversationState() {
        AppConfig config = new AppConfig();
        ConversationState state = new ConversationState();

        CommandContext ctx = new CommandContext(config);

        PlanCommand cmd = new PlanCommand(null, config, state);
        cmd.savePlan(ctx, "original request", "the plan");

        assertEquals("the plan", state.getPendingPlan());
        assertEquals("original request", state.getPlanRequest());
    }

    @Test
    public void testSavePlanHandlesNullAgentLoop() {
        PlanCommand cmd = new PlanCommand(null, new AppConfig(), null);
        CommandContext ctx = new CommandContext(new AppConfig());
        assertDoesNotThrow(() -> cmd.savePlan(ctx, "req", "plan"));
    }

    private static LLMResponse response(String content) {
        LLMResponse r = new LLMResponse();
        r.setContent(content);
        return r;
    }
}
