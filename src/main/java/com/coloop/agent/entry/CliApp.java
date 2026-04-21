package com.coloop.agent.entry;

import com.coloop.agent.capability.provider.openai.OpenAICompatibleProvider;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.runtime.AgentRuntime;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;

public class CliApp {
    public static void main(String[] args) {
        System.out.println("=== coloop-agent CLI ===\n");

        AppConfig config;
        try {
            config = AppConfig.fromSetting("coloop-agent-setting.json");
        } catch (Exception e) {
            System.out.println("Failed to load config: " + e.getMessage());
            return;
        }

        LLMProvider provider = new OpenAICompatibleProvider(config.getModelConfig("minimax"));

        AgentRuntime runtime = new CapabilityLoader()
            .withCapability(StandardCapability.EXEC_TOOL, config)
            .withCapability(StandardCapability.READ_FILE_TOOL, config)
            .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
            .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
            .withCapability(StandardCapability.SEARCH_FILES_TOOL, config)
            .withCapability(StandardCapability.LIST_DIRECTORY_TOOL, config)
            .withCapability(StandardCapability.BASE_PROMPT, config)
            .withCapability(StandardCapability.AGENTS_MD_PROMPT, config)
            .withCapability(StandardCapability.LOGGING_HOOK, config)
            .withCapability(StandardCapability.MCP_CLIENT, config)
            .build(provider, config);

        String result = runtime.chat("简单整理一下今日新闻");
        System.out.println("\n[真实 API 模式] 最终结果：");
        System.out.println(result);
    }
}
