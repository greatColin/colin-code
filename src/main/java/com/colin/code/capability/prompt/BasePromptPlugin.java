package com.colin.code.capability.prompt;

import com.colin.code.core.prompt.PromptPlugin;
import com.colin.code.runtime.config.AppConfig;

import java.util.Map;

public class BasePromptPlugin implements PromptPlugin {
    @Override
    public String getName() { return "base"; }

    @Override
    public int getPriority() { return 0; }

    @Override
    public String generate(AppConfig config, Map<String, Object> ctx) {
        return "你是一个帮助用户完成软件工程任务的 AI 助手。\n"
             + "Current time: " + ctx.get("time") + "\n"
             + "Working directory: " + ctx.get("cwd") + "\n"
             + "Platform: " + ctx.get("os") + "\n"
             + "Model: " + config.getModel() + "\n\n"
             + "Always respond in the same language as the user's message.";
    }
}
