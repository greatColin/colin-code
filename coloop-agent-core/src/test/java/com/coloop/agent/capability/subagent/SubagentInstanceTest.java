package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentLoop;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubagentInstanceTest {

    @Test
    void testAllFieldsSetCorrectly() {
        AgentLoop mockLoop = null; // null OK for data model test
        long before = System.currentTimeMillis();
        SubagentInstance inst = new SubagentInstance(
            "planner", "Plan the approach",
            "You are a planner.", List.of("read", "write"),
            mockLoop
        );
        long after = System.currentTimeMillis();

        assertEquals("planner", inst.name);
        assertEquals("Plan the approach", inst.description);
        assertEquals("You are a planner.", inst.systemPrompt);
        assertEquals(List.of("read", "write"), inst.toolNames);
        assertSame(mockLoop, inst.agentLoop);
        assertNotNull(inst.runLock);
        assertTrue(inst.createdAt >= before && inst.createdAt <= after);
        assertFalse(inst.running);
    }

    @Test
    void testRunningFlagCanBeToggled() {
        SubagentInstance inst = new SubagentInstance(
            "a", "d", "sp", List.of(), null
        );
        assertFalse(inst.running);
        inst.running = true;
        assertTrue(inst.running);
        inst.running = false;
        assertFalse(inst.running);
    }

    @Test
    void testNullToolNamesMeansInheritParentTools() {
        // null toolNames signals "use all parent tools" per spec section 2.4
        SubagentInstance inst = new SubagentInstance(
            "nulltools", "desc", "sp", null, null
        );
        assertNull(inst.toolNames);
    }

    @Test
    void testRunLockIsSameObjectAcrossReads() {
        SubagentInstance inst = new SubagentInstance(
            "locktest", "desc", "sp", List.of(), null
        );
        Object lock1 = inst.runLock;
        Object lock2 = inst.runLock;
        assertSame(lock1, lock2);
    }
}
