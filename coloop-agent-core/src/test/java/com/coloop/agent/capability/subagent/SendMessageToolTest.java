package com.coloop.agent.capability.subagent;

import com.coloop.agent.capability.provider.mock.MockProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMResponse;
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

class SendMessageToolTest {

    private SubagentRegistry registry;
    private AppConfig config;

    @BeforeEach
    void setUp() throws IOException {
        registry = new SubagentRegistry();
        config = AppConfig.fromSetting("coloop-agent-setting.json");
    }

    @Test
    void testGetNameReturnsSendMessage() {
        SendMessageTool tool = new SendMessageTool(registry);
        assertEquals("SendMessage", tool.getName());
    }

    @Test
    void testTargetNotFoundReturnsError() {
        SendMessageTool tool = new SendMessageTool(registry);
        String result = tool.execute(Map.of("to", "nonexistent", "message", "Hello"));
        assertTrue(result.startsWith("Error: subagent 'nonexistent' not found"));
    }

    @Test
    void testMissingToReturnsError() {
        SendMessageTool tool = new SendMessageTool(registry);
        String result = tool.execute(Map.of("message", "Hello"));
        assertTrue(result.startsWith("Error: missing required field 'to'"));
    }

    @Test
    void testMissingMessageReturnsError() {
        SendMessageTool tool = new SendMessageTool(registry);
        String result = tool.execute(Map.of("to", "planner"));
        assertTrue(result.startsWith("Error: missing required field 'message'"));
    }

    @Test
    void testSendsMessageAndReturnsResponse() throws IOException {
        // Pre-register a subagent
        MockProvider provider = new MockProvider(
            List.of(new LLMResponse() {{ setContent("second response"); }})
        );
        StandardMessageBuilder mb = new StandardMessageBuilder(
            List.of(new SubagentPromptPlugin("You are a planner.")), config);
        AgentLoop loop = new AgentLoop(provider, new ToolRegistry(), mb,
            Collections.emptyList(), Collections.emptyList(), config);
        SubagentInstance inst = new SubagentInstance("planner", "desc",
            "You are a planner.", null, loop);
        registry.createOrReplace("planner", inst);

        SendMessageTool tool = new SendMessageTool(registry);
        String result = tool.execute(Map.of("to", "planner", "message", "Change plan to Y"));

        assertTrue(result.contains("second response"));
    }

    @Test
    void testOptionalSummaryAccepted() throws IOException {
        // Pre-register a subagent
        MockProvider provider = new MockProvider(
            List.of(new LLMResponse() {{ setContent("ok"); }})
        );
        StandardMessageBuilder mb = new StandardMessageBuilder(
            List.of(new SubagentPromptPlugin("sp")), config);
        AgentLoop loop = new AgentLoop(provider, new ToolRegistry(), mb,
            Collections.emptyList(), Collections.emptyList(), config);
        registry.createOrReplace("helper", new SubagentInstance(
            "helper", "desc", "sp", null, loop));

        SendMessageTool tool = new SendMessageTool(registry);
        String result = tool.execute(Map.of(
            "to", "helper", "message", "Hello", "summary", "Quick question"));

        assertTrue(result.contains("ok"));
    }
}
