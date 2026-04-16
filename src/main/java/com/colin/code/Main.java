package com.colin.code;

import com.colin.code.agent.AgentLoop;
import com.colin.code.runtime.config.AppConfig;
import com.colin.code.provider.LLMProvider;
import com.colin.code.provider.LLMResponse;
import com.colin.code.provider.MockProvider;
import com.colin.code.provider.OpenAICompatibleProvider;
import com.colin.code.provider.ToolCallRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Demo 入口：展示一个最小可用的 agent-loop + exec 工具流程。
 *
 * 运行方式：
 * 1. Mock 模式（无需网络）：直接运行 main，默认使用 createMockProvider()
 * 2. 真实 API 模式：设置环境变量 OPENAI_API_KEY，并切换 provider = new OpenAICompatibleProvider(config)
 *
 * 编译运行：mvn compile exec:java -Dexec.mainClass="com.colin.code.Main"
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== colin-code AgentLoop Demo ===\n");

        // 模式一：Mock 测试（不依赖外部 API，验证 loop 逻辑）
        runWithMock();

        // 模式二：真实 API（需配置 OPENAI_API_KEY）
//         runWithRealAPI();
    }

    /** 使用 MockProvider 模拟两轮对话：第一轮调用 exec 工具，第二轮返回总结 */
    private static void runWithMock() {
        AppConfig config = new AppConfig();
        LLMProvider provider = buildMockProvider();
        AgentLoop agent = new AgentLoop(config, provider);

        String result = agent.chat("帮我列出当前目录的文件");

        System.out.println("[Mock 模式] 最终结果：");
        System.out.println(result);
        System.out.println();
    }

    /** 使用真实的 OpenAI 兼容 API */
    private static void runWithRealAPI() {
        AppConfig config = new AppConfig();
        if (config.getApiKey().isEmpty()) {
            System.out.println("请先设置环境变量 OPENAI_API_KEY");
            return;
        }
        LLMProvider provider = new OpenAICompatibleProvider(config);
        AgentLoop agent = new AgentLoop(config, provider);

        String result = agent.chat("帮我看一下本地ip，用中文回答");

        System.out.println("[真实 API 模式] 最终结果：");
        System.out.println(result);
    }

    // ==================== 以下仅为构造 Mock 响应的细节 ====================

    private static LLMProvider buildMockProvider() {
        List<LLMResponse> responses = new ArrayList<>();

        // 第一轮：LLM 决定调用 exec 工具
        LLMResponse r1 = new LLMResponse();
        r1.setContent("我来帮你查看当前目录的文件列表。");
        ToolCallRequest tc = new ToolCallRequest();
        tc.setId("call_1");
        tc.setName("exec");
        tc.setArguments(Collections.singletonMap("command", "ls -la"));
        r1.setToolCalls(Collections.singletonList(tc));
        responses.add(r1);

        // 第二轮：LLM 根据工具结果给出最终回复
        LLMResponse r2 = new LLMResponse();
        r2.setContent("当前目录包含 pom.xml、src、target 等文件和文件夹。");
        responses.add(r2);

        return new MockProvider(responses);
    }
}
