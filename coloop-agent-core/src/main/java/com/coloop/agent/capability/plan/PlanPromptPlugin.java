package com.coloop.agent.capability.plan;

import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.Map;

public class PlanPromptPlugin implements PromptPlugin {

    @Override
    public String getName() {
        return "plan_mode";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String generate(AppConfig config, Map<String, Object> runtimeContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Plan Mode\n\n");
        sb.append("You are in Plan Mode. Your task is to analyze the request and the codebase, ");
        sb.append("then draft a clear, step-by-step execution plan.\n\n");
        sb.append("MANDATORY RULES:\n");
        sb.append("1. You may ONLY use read and exploratory tools (read_file, search_files, list_directory, exec).\n");
        sb.append("2. Do NOT write, edit, or delete any files while in Plan Mode.\n");
        sb.append("3. Output a numbered plan with specific file paths and expected changes.\n");
        sb.append("4. Keep the plan concrete and actionable — one step per file or logical unit.\n");
        return sb.toString().trim();
    }
}
