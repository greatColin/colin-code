package com.coloop.agent.capability.subagent;

import com.coloop.agent.capability.provider.mock.MockProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.capability.message.StandardMessageBuilder;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentToolTest {

    private SubagentRegistry registry;
    private AppConfig config;

    @BeforeEach
    void setUp() throws IOException {
        registry = new SubagentRegistry();
        config = AppConfig.fromSetting("coloop-agent-setting.json");
    }

    @Test
    void testGetNameReturnsAgent() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        assertEquals("Agent", tool.getName());
    }

    @Test
    void testGetDescriptionIsNonEmpty() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void testParametersIncludeRequiredFields() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        Map<String, Object> params = tool.getParameters();
        assertTrue(params.containsKey("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) params.get("properties");
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("description"));
        assertTrue(props.containsKey("system_prompt"));
        assertTrue(props.containsKey("prompt"));
        assertTrue(props.containsKey("tool_names"));
    }

    @Test
    void testMissingNameReturnsError() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        String result = tool.execute(Map.of("description", "d", "system_prompt", "sp", "prompt", "p"));
        assertTrue(result.startsWith("Error: missing required field 'name'"));
    }

    @Test
    void testMissingDescriptionReturnsError() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        String result = tool.execute(Map.of("name", "n", "system_prompt", "sp", "prompt", "p"));
        assertTrue(result.startsWith("Error: missing required field 'description'"));
    }

    @Test
    void testMissingSystemPromptReturnsError() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        String result = tool.execute(Map.of("name", "n", "description", "d", "prompt", "p"));
        assertTrue(result.startsWith("Error: missing required field 'system_prompt'"));
    }

    @Test
    void testMissingPromptReturnsError() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        String result = tool.execute(Map.of("name", "n", "description", "d", "system_prompt", "sp"));
        assertTrue(result.startsWith("Error: missing required field 'prompt'"));
    }

    @Test
    void testEmptyStringTreatedAsMissing() {
        AgentTool tool = new AgentTool(registry, (name, sp, tn, mk) -> null);
        String result = tool.execute(Map.of(
            "name", "", "description", "d", "system_prompt", "sp", "prompt", "p"));
        assertTrue(result.startsWith("Error: missing required field 'name'"));
    }

    @Test
    void testCreatesSubagentAndReturnsResponse() throws IOException {
        // Build a trivial subagent loop that returns a fixed response
        SubagentLoopFactory factory = (name, sp, tn, mk) -> {
            MockProvider provider = new MockProvider(
                List.of(new LLMResponse() {{ setContent("I am a subagent response."); }})
            );
            StandardMessageBuilder mb = new StandardMessageBuilder(
                List.of(new SubagentPromptPlugin(sp)), config);
            return new AgentLoop(provider, new ToolRegistry(), mb,
                Collections.emptyList(), Collections.emptyList(), config);
        };

        AgentTool tool = new AgentTool(registry, factory);
        String result = tool.execute(Map.of(
            "name", "planner",
            "description", "Plans things",
            "system_prompt", "You are a planner.",
            "prompt", "Plan X"
        ));

        assertTrue(result.contains("I am a subagent response"));
        assertNotNull(registry.get("planner"));
        assertFalse(registry.get("planner").running);
    }

    @Test
    void testSameNameReplacesOldInstance() throws IOException {
        SubagentLoopFactory factory = (name, sp, tn, mk) -> {
            MockProvider provider = new MockProvider(
                List.of(new LLMResponse() {{ setContent("response"); }})
            );
            StandardMessageBuilder mb = new StandardMessageBuilder(
                List.of(new SubagentPromptPlugin(sp)), config);
            return new AgentLoop(provider, new ToolRegistry(), mb,
                Collections.emptyList(), Collections.emptyList(), config);
        };

        AgentTool tool = new AgentTool(registry, factory);

        // First call
        tool.execute(Map.of("name", "x", "description", "d1",
            "system_prompt", "sp", "prompt", "p1"));

        // Second call with same name (triggers replace)
        String result = tool.execute(Map.of("name", "x", "description", "d2",
            "system_prompt", "sp", "prompt", "p2"));

        assertTrue(result.contains("response"));
        SubagentInstance inst = registry.get("x");
        assertNotNull(inst);
        assertEquals("d2", inst.description);
    }

    @Test
    void testAgentAndSendMessageStripedFromToolNames() throws IOException {
        // Create a tool that reports what toolNames the factory received
        final List<String>[] capturedNames = new List[1];
        SubagentLoopFactory factory = (name, sp, tn, mk) -> {
            capturedNames[0] = tn;
            MockProvider provider = new MockProvider(
                List.of(new LLMResponse() {{ setContent("ok"); }})
            );
            StandardMessageBuilder mb = new StandardMessageBuilder(
                List.of(new SubagentPromptPlugin(sp)), config);
            return new AgentLoop(provider, new ToolRegistry(), mb,
                Collections.emptyList(), Collections.emptyList(), config);
        };

        AgentTool tool = new AgentTool(registry, factory);
        tool.execute(Map.of(
            "name", "clean", "description", "d",
            "system_prompt", "sp", "prompt", "p",
            "tool_names", List.of("read", "Agent", "write", "SendMessage")
        ));

        assertNotNull(capturedNames[0]);
        assertFalse(capturedNames[0].contains("Agent"));
        assertFalse(capturedNames[0].contains("SendMessage"));
        assertTrue(capturedNames[0].contains("read"));
        assertTrue(capturedNames[0].contains("write"));
    }

    @Test
    void testToolNamesNullPassesNullToFactory() throws IOException {
        final List<String>[] capturedNames = new List[1];
        SubagentLoopFactory factory = (name, sp, tn, mk) -> {
            capturedNames[0] = tn;
            MockProvider provider = new MockProvider(
                List.of(new LLMResponse() {{ setContent("ok"); }})
            );
            StandardMessageBuilder mb = new StandardMessageBuilder(
                List.of(new SubagentPromptPlugin(sp)), config);
            return new AgentLoop(provider, new ToolRegistry(), mb,
                Collections.emptyList(), Collections.emptyList(), config);
        };

        AgentTool tool = new AgentTool(registry, factory);
        tool.execute(Map.of(
            "name", "def", "description", "d",
            "system_prompt", "sp", "prompt", "p"
        ));

        assertNull(capturedNames[0]); // null means "use all parent tools"
    }
}
