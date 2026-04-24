package com.coloop.agent.capability.plan;

import com.coloop.agent.core.context.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlanInjectionHookTest {

    @Test
    public void testInjectsPlanWhenPending() {
        ConversationState state = new ConversationState();
        state.setPendingPlan("step 1: do X");

        PlanInjectionHook hook = new PlanInjectionHook(state);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "you are agent"));
        messages.add(Map.of("role", "user", "content", "y"));

        hook.beforeLLMCall(messages);

        // Should have 3 messages: system, plan-injection, user-confirmation
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(1).get("role"));
        assertTrue(((String) messages.get(1).get("content")).contains("step 1: do X"));
        assertTrue(((String) messages.get(1).get("content")).contains("Approved plan"));
    }

    @Test
    public void testClearsPendingPlanAfterInjection() {
        ConversationState state = new ConversationState();
        state.setPendingPlan("some plan");

        PlanInjectionHook hook = new PlanInjectionHook(state);
        hook.beforeLLMCall(new ArrayList<>(List.of(
            Map.of("role", "user", "content", "ok")
        )));

        assertNull(state.getPendingPlan(), "Pending plan should be cleared after injection");
    }

    @Test
    public void testDoesNothingWhenNoPendingPlan() {
        ConversationState state = new ConversationState();

        PlanInjectionHook hook = new PlanInjectionHook(state);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hello"));

        hook.beforeLLMCall(messages);

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).get("content"));
    }
}
