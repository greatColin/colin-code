package com.coloop.agent.capability.subagent;

import com.coloop.agent.runtime.CompositeCapability;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubagentManagementCapabilityTest {

    @Test
    void testImplementsCompositeCapability() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null);
        assertTrue(cap instanceof CompositeCapability);
    }

    @Test
    void testGetToolsReturnsAgentAndSendMessage() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null);
        assertEquals(2, cap.getTools().size());
        assertEquals("Agent", cap.getTools().get(0).getName());
        assertEquals("SendMessage", cap.getTools().get(1).getName());
    }

    @Test
    void testGetPromptPluginReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null);
        assertNull(cap.getPromptPlugin());
    }

    @Test
    void testGetHookReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null);
        assertNull(cap.getHook());
    }

    @Test
    void testGetRegistryReturnsSameInstance() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null);
        assertSame(registry, cap.getRegistry());
    }
}
