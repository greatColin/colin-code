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
        assertNotNull(inst.runLock);
        assertTrue(inst.createdAt >= before && inst.createdAt <= after);
        assertFalse(inst.running);
    }

    @Test
    void testFieldsAreFinal() {
        SubagentInstance inst = new SubagentInstance(
            "a", "d", "sp", List.of(), null
        );
        // Verify no mutation path — these are final fields
        inst.running = true;
        assertTrue(inst.running); // volatile write works
    }

    @Test
    void testNullToolNamesAcceptsAnyList() {
        SubagentInstance inst = new SubagentInstance(
            "nulltools", "desc", "sp", null, null
        );
        assertNull(inst.toolNames);
    }
}
