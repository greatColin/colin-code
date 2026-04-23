package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.Map;

/**
 * 模型命令：/model — 查看或切换当前模型。
 *
 * <p>不带参数时列出可用模型；带参数时尝试切换（当前仅展示信息，
 * 完整切换需要重建 AgentLoop 和 Provider）。</p>
 */
public class ModelCommand implements Command {

    @Override
    public String getName() {
        return "model";
    }

    @Override
    public String getDescription() {
        return "List or switch the current LLM model";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        AppConfig config = ctx.getAppConfig();
        if (config == null) {
            return CommandResult.success("No model configuration available.");
        }

        Map<String, AppConfig.ModelConfig> models = config.getModels();
        if (models == null || models.isEmpty()) {
            return CommandResult.success("No models configured.");
        }

        if (args != null && !args.isEmpty()) {
            String target = args.trim();
            if (models.containsKey(target)) {
                AppConfig.ModelConfig mc = models.get(target);
                return CommandResult.success(
                        "Model '" + target + "' selected (" + mc.getModel() + ").\n" +
                        "Note: full model switching requires a new session (/new)."
                );
            }
            return CommandResult.success("Unknown model: '" + target + "'. Available: " + String.join(", ", models.keySet()));
        }

        StringBuilder sb = new StringBuilder("Available models:\n");
        String defaultModel = config.getDefaultModel();
        for (Map.Entry<String, AppConfig.ModelConfig> entry : models.entrySet()) {
            String name = entry.getKey();
            AppConfig.ModelConfig mc = entry.getValue();
            String marker = name.equals(defaultModel) ? " [default]" : "";
            sb.append("  ").append(name).append(marker)
              .append(": ").append(mc.getModel())
              .append(" @ ").append(mc.getApiBase())
              .append("\n");
        }
        sb.append("\nUsage: /model <model-name> to select a model for the next session.");
        return CommandResult.success(sb.toString().trim());
    }
}
