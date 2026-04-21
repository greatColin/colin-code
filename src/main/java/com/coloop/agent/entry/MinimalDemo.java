package com.coloop.agent.entry;

import com.coloop.agent.capability.provider.mock.MockProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinimalDemo {
    public static void main(String[] args) {
        System.out.println("=== coloop-agent Minimal Demo ===\n");

        AppConfig config;
        try {
            config = AppConfig.fromSetting("coloop-agent-setting.json");
        } catch (Exception e) {
            System.out.println("Failed to load config, using default: " + e.getMessage());
            config = AppConfig.fromEnv();
        }
        LLMProvider provider = buildMockProvider();

        AgentLoop agentLoop = new CapabilityLoader()
            .withCapability(StandardCapability.EXEC_TOOL, config)
            .withCapability(StandardCapability.READ_FILE_TOOL, config)
            .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
            .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
            .withCapability(StandardCapability.SEARCH_FILES_TOOL, config)
            .withCapability(StandardCapability.LIST_DIRECTORY_TOOL, config)
            .withCapability(StandardCapability.MCP_CLIENT, config)
            .withCapability(StandardCapability.BASE_PROMPT, config)
            .build(provider, config);

        String result = agentLoop.chat("帮我读取 pom.xml 的前 10 行");
        System.out.println("\n[Mock 模式] 最终结果：");
        System.out.println(result);
    }

    private static LLMProvider buildMockProvider() {
        List<LLMResponse> responses = new ArrayList<>();

        LLMResponse r1 = new LLMResponse();
        r1.setContent("我来帮你读取 pom.xml 的前 10 行。");
        ToolCallRequest tc = new ToolCallRequest();
        tc.setId("call_1");
        tc.setName("read_file");
        tc.setArguments(Collections.<String, Object>singletonMap("file_path", "F:/code2024/colin-code/pom.xml"));
        r1.setToolCalls(Collections.singletonList(tc));
        responses.add(r1);

        LLMResponse r2 = new LLMResponse();
        r2.setContent("pom.xml 的前 10 行已读取完成。");
        responses.add(r2);

        return new MockProvider(responses);
    }
}
