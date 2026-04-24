package com.coloop.agent.capability.plan;

import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlanPromptPluginTest {

    @Test
    public void testContainsPlanModeDirective() {
        PlanPromptPlugin plugin = new PlanPromptPlugin();
        String result = plugin.generate(new AppConfig(), Map.of());

        assertNotNull(result);
        assertTrue(result.contains("Plan Mode"), "Should mention Plan Mode");
        assertTrue(result.contains("read") || result.contains("analyze"), "Should direct analysis");
    }

    @Test
    public void testNameAndPriority() {
        PlanPromptPlugin plugin = new PlanPromptPlugin();
        assertEquals("plan_mode", plugin.getName());
        assertTrue(plugin.getPriority() > 0);
    }
}
