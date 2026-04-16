package com.colin.code.capability.prompt;

import com.colin.code.core.prompt.PromptPlugin;
import com.colin.code.runtime.config.AppConfig;

import java.util.Map;

public class SkillPromptPlugin implements PromptPlugin {
    @Override
    public String getName() { return "skill"; }

    @Override
    public int getPriority() { return 10; }

    @Override
    public String generate(AppConfig config, Map<String, Object> ctx) {
        return "";
    }
}
