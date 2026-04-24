package com.coloop.agent.capability.plan;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlanCapabilityTest {

    @Test
    public void testBundlesComponents() {
        PlanCapability cap = new PlanCapability(null, new AppConfig());

        assertNotNull(cap.getPlanCommand());
        assertNotNull(cap.getCancelCommand());

        List<Tool> tools = cap.getTools();
        assertTrue(tools.isEmpty(), "PlanCapability does not expose tools directly");

        PromptPlugin plugin = cap.getPromptPlugin();
        assertNotNull(plugin);
        assertEquals("plan_mode", plugin.getName());

        AgentHook hook = cap.getHook();
        assertNotNull(hook);
    }
}
