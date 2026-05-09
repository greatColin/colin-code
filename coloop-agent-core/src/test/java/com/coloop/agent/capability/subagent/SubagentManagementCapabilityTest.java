package com.coloop.agent.capability.subagent;

import com.coloop.agent.runtime.CompositeCapability;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubagentManagementCapabilityTest {

    @Test
    void testImplementsCompositeCapability() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertTrue(cap instanceof CompositeCapability);
    }

    @Test
    void testGetToolsReturnsAllThree() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertEquals(3, cap.getTools().size());
        assertEquals("Agent", cap.getTools().get(0).getName());
        assertEquals("SendMessage", cap.getTools().get(1).getName());
        assertEquals("ListModels", cap.getTools().get(2).getName());
    }

    @Test
    void testGetPromptPluginReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertNull(cap.getPromptPlugin());
    }

    @Test
    void testGetHookReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertNull(cap.getHook());
    }

    @Test
    void testGetRegistryReturnsSameInstance() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertSame(registry, cap.getRegistry());
    }
}
