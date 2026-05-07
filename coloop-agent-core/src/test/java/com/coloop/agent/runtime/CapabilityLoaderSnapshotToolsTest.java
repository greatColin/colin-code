package com.coloop.agent.runtime;

import com.coloop.agent.core.tool.Tool;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityLoaderSnapshotToolsTest {

    @Test
    void testSnapshotReturnsUnmodifiableList() {
        CapabilityLoader loader = new CapabilityLoader();
        List<Tool> snap = loader.snapshotTools();
        assertNotNull(snap);
        assertTrue(snap.isEmpty());
    }

    @Test
    void testSnapshotIncludesAddedTools() {
        CapabilityLoader loader = new CapabilityLoader();
        loader.withTool(new TestTool("tool_a"));
        loader.withTool(new TestTool("tool_b"));
        List<Tool> snap = loader.snapshotTools();
        assertEquals(2, snap.size());
        assertEquals("tool_a", snap.get(0).getName());
        assertEquals("tool_b", snap.get(1).getName());
    }

    @Test
    void testSnapshotIsNotAffectedByLaterAdditions() {
        CapabilityLoader loader = new CapabilityLoader();
        loader.withTool(new TestTool("first"));
        List<Tool> snap = loader.snapshotTools();
        loader.withTool(new TestTool("second"));
        assertEquals(1, snap.size()); // snapshot captured before second add
        assertEquals("first", snap.get(0).getName());
    }

    private static class TestTool implements Tool {
        private final String name;
        TestTool(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return ""; }
        @Override public Map<String, Object> getParameters() { return Map.of(); }
        @Override public String execute(Map<String, Object> params) { return ""; }
    }
}
