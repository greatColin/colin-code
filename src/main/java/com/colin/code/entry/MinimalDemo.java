package com.colin.code.entry;

import com.colin.code.capability.provider.mock.MockProvider;
import com.colin.code.core.provider.LLMProvider;
import com.colin.code.core.provider.LLMResponse;
import com.colin.code.core.provider.ToolCallRequest;
import com.colin.code.runtime.AgentRuntime;
import com.colin.code.runtime.CapabilityLoader;
import com.colin.code.runtime.StandardCapability;
import com.colin.code.runtime.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinimalDemo {
    public static void main(String[] args) {
        System.out.println("=== colin-code Minimal Demo ===\n");

        AppConfig config = new AppConfig();
        LLMProvider provider = buildMockProvider();

        AgentRuntime runtime = new CapabilityLoader()
            .withCapability(StandardCapability.EXEC_TOOL, config)
            .withCapability(StandardCapability.BASE_PROMPT, config)
            .build(provider, config);

        String result = runtime.chat("帮我列出当前目录的文件");
        System.out.println("\n[Mock 模式] 最终结果：");
        System.out.println(result);
    }

    private static LLMProvider buildMockProvider() {
        List<LLMResponse> responses = new ArrayList<LLMResponse>();

        LLMResponse r1 = new LLMResponse();
        r1.setContent("我来帮你查看当前目录的文件列表。");
        ToolCallRequest tc = new ToolCallRequest();
        tc.setId("call_1");
        tc.setName("exec");
        tc.setArguments(Collections.<String, Object>singletonMap("command", "ls -la"));
        r1.setToolCalls(Collections.singletonList(tc));
        responses.add(r1);

        LLMResponse r2 = new LLMResponse();
        r2.setContent("当前目录包含 pom.xml、src、target 等文件和文件夹。");
        responses.add(r2);

        return new MockProvider(responses);
    }
}
