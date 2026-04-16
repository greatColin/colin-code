package com.colin.code.capability.prompt;

import com.colin.code.core.prompt.PromptPlugin;
import com.colin.code.runtime.config.AppConfig;

import com.colin.code.core.prompt.PromptPlugin;
import com.colin.code.runtime.config.AppConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class AgentsMdPromptPlugin implements PromptPlugin {
    @Override
    public String getName() { return "agents_md"; }

    @Override
    public int getPriority() { return 20; }

    @Override
    public String generate(AppConfig config, Map<String, Object> ctx) {
        Path agentsMd = Paths.get((String) ctx.get("cwd"), "AGENTS.md");
        if (!Files.exists(agentsMd)) return "";
        try {
            return "## 项目约定（AGENTS.md）\n" + new String(Files.readAllBytes(agentsMd), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
