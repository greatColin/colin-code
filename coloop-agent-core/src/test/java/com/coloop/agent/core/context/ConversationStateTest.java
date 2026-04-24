package com.coloop.agent.core.context;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConversationStateTest {

    @Test
    public void testPendingPlanRoundTrip() {
        ConversationState state = new ConversationState();
        state.setPendingPlan("step 1: read config");
        assertEquals("step 1: read config", state.getPendingPlan());
    }

    @Test
    public void testPlanRequestRoundTrip() {
        ConversationState state = new ConversationState();
        state.setPlanRequest("implement auth");
        assertEquals("implement auth", state.getPlanRequest());
    }

    @Test
    public void testDefaultsAreNull() {
        ConversationState state = new ConversationState();
        assertNull(state.getPendingPlan());
        assertNull(state.getPlanRequest());
    }
}
