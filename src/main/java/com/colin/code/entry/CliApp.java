package com.colin.code.entry;

import com.colin.code.capability.provider.openai.OpenAICompatibleProvider;
import com.colin.code.core.provider.LLMProvider;
import com.colin.code.runtime.AgentRuntime;
import com.colin.code.runtime.CapabilityLoader;
import com.colin.code.runtime.StandardCapability;
import com.colin.code.runtime.config.AppConfig;

public class CliApp {
    public static void main(String[] args) {
        System.out.println("=== colin-code CLI ===\n");

        AppConfig config = new AppConfig();

        if (config.getApiKey().isEmpty()) {
            System.out.println("真实 API 模式需要设置环境变量 OPENAI_API_KEY，当前切换到 MinimalDemo 模式。\n");
            MinimalDemo.main(args);
            return;
        }

        LLMProvider provider = new OpenAICompatibleProvider(config);

        AgentRuntime runtime = new CapabilityLoader()
            .withCapability(StandardCapability.EXEC_TOOL, config)
            .withCapability(StandardCapability.BASE_PROMPT, config)
            .withCapability(StandardCapability.LOGGING_HOOK, config)
            .build(provider, config);

        String result = runtime.chat("帮我看一下本地ip，用中文回答");
        System.out.println("\n[真实 API 模式] 最终结果：");
        System.out.println(result);
    }
}
