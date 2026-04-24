package com.coloop.agent.capability.plan;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.context.ConversationState;

import java.util.List;
import java.util.Map;

public class PlanInjectionHook implements AgentHook {

    private final ConversationState conversationState;

    public PlanInjectionHook(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        String pendingPlan = conversationState.getPendingPlan();
        if (pendingPlan == null || pendingPlan.isEmpty()) {
            return;
        }

        injectPlanIntoMessages(messages, pendingPlan);
        conversationState.setPendingPlan(null);
    }

    // Step 1: Locate the last user message (the confirmation input)
    // Step 2: Remove it temporarily
    // Step 3: Insert plan context message before it
    // Step 4: Re-append the original confirmation
    private void injectPlanIntoMessages(List<Map<String, Object>> messages, String plan) {
        int lastIdx = messages.size() - 1;
        if (lastIdx < 0) {
            return;
        }

        Map<String, Object> lastMsg = messages.get(lastIdx);
        if (!"user".equals(lastMsg.get("role"))) {
            // If last message is not user, append plan as user message at end
            messages.add(buildPlanMessage(plan));
            return;
        }

        // Remove the confirmation message temporarily
        messages.remove(lastIdx);
        // Insert plan context before the confirmation
        messages.add(buildPlanMessage(plan));
        // Re-append the original user confirmation
        messages.add(lastMsg);
    }

    private Map<String, Object> buildPlanMessage(String plan) {
        return Map.of(
            "role", "user",
            "content", "Approved plan:\n\n" + plan + "\n\nProceed with execution."
        );
    }
}
