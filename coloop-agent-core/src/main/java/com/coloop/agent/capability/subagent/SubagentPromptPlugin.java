package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.runtime.config.AppConfig;
import java.util.Map;

public class SubagentPromptPlugin implements PromptPlugin {

    private final String systemPrompt;

    public SubagentPromptPlugin(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "subagent_prompt";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String generate(AppConfig config, Map<String, Object> runtimeContext) {
        return systemPrompt;
    }
}
