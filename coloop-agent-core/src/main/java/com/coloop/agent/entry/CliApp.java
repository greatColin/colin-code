package com.coloop.agent.entry;

import com.coloop.agent.capability.command.CommandInterceptor;
import com.coloop.agent.capability.command.CommandScanner;
import com.coloop.agent.capability.command.CompactCommand;
import com.coloop.agent.capability.command.ExitCommand;
import com.coloop.agent.capability.command.HelpCommand;
import com.coloop.agent.capability.command.ModelCommand;
import com.coloop.agent.capability.command.NewSessionCommand;
import com.coloop.agent.capability.provider.openai.OpenAICompatibleProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;
import com.coloop.agent.runtime.runtime.LoopInputAgentRuntime;

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

        // 组装命令系统
        CommandRegistry cmdRegistry = new CommandRegistry();
        cmdRegistry.register(new ExitCommand());
        cmdRegistry.register(new NewSessionCommand());
        cmdRegistry.register(new CompactCommand());
        cmdRegistry.register(new ModelCommand());
        cmdRegistry.register(new HelpCommand(cmdRegistry));
        CommandScanner.scanUserCommands(cmdRegistry);

        CommandContext cmdCtx = new CommandContext(config);
        CommandInterceptor cmdInterceptor = new CommandInterceptor(cmdRegistry, cmdCtx);

        // 构建 AgentLoop
        AgentLoop agentLoop = new CapabilityLoader()
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
            .withInterceptor(cmdInterceptor)
            .build(provider, config);

        // 用户问答，允许追问
        LoopInputAgentRuntime runtime = new LoopInputAgentRuntime(agentLoop);

        // 补全 CommandContext 的循环依赖引用
        cmdCtx.setAgentLoop(agentLoop);
        cmdCtx.setTerminator(() -> runtime.stop());

        runtime.chat();
    }
}
