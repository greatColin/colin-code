package com.coloop.agent.capability.subagent;

import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubagentPromptPluginTest {

    @Test
    void testReturnsName() {
        SubagentPromptPlugin plugin = new SubagentPromptPlugin("You are a helper.");
        assertEquals("subagent_prompt", plugin.getName());
    }

    @Test
    void testPriorityIsZero() {
        SubagentPromptPlugin plugin = new SubagentPromptPlugin("sys");
        assertEquals(0, plugin.getPriority());
    }

    @Test
    void testGenerateReturnsSystemPrompt() throws IOException {
        SubagentPromptPlugin plugin = new SubagentPromptPlugin("You are a planning agent.");
        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
        String result = plugin.generate(config, Map.of());
        assertEquals("You are a planning agent.", result);
    }

    @Test
    void testGenerateWithEmptyPromptReturnsEmpty() throws IOException {
        SubagentPromptPlugin plugin = new SubagentPromptPlugin("");
        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
        String result = plugin.generate(config, Map.of());
        assertEquals("", result);
    }
}
