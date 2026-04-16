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

        AppConfig config = AppConfig.fromEnv();

        if (config.getApiKey().isEmpty()) {
            System.out.println("真实 API 模式需要设置环境变量 COLIN_CODE_OPENAI_API_KEY（或 OPENAI_API_KEY），当前切换到 MinimalDemo 模式。\n");
            MinimalDemo.main(args);
            return;
        }

        LLMProvider provider = new OpenAICompatibleProvider(config);

        AgentRuntime runtime = new CapabilityLoader()
            .withCapability(StandardCapability.EXEC_TOOL, config)
            .withCapability(StandardCapability.READ_FILE_TOOL, config)
            .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
            .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
            .withCapability(StandardCapability.SEARCH_FILES_TOOL, config)
            .withCapability(StandardCapability.LIST_DIRECTORY_TOOL, config)
            .withCapability(StandardCapability.BASE_PROMPT, config)
            .withCapability(StandardCapability.LOGGING_HOOK, config)
            .build(provider, config);

        String result = runtime.chat("帮我看一下本地ip，用中文回答");
        System.out.println("\n[真实 API 模式] 最终结果：");
        System.out.println(result);
    }
}
