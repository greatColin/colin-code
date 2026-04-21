package com.coloop.agent.runtime;

import com.coloop.agent.capability.CapabilityType;
import com.coloop.agent.capability.hook.LoggingHook;
import com.coloop.agent.capability.mcp.McpCapability;
import com.coloop.agent.capability.prompt.AgentsMdPromptPlugin;
import com.coloop.agent.capability.prompt.BasePromptPlugin;
import com.coloop.agent.capability.prompt.SkillPromptPlugin;
import com.coloop.agent.capability.tool.exec.ExecTool;
import com.coloop.agent.capability.tool.filesystem.ReadFileTool;
import com.coloop.agent.capability.tool.filesystem.WriteFileTool;
import com.coloop.agent.capability.tool.filesystem.EditFileTool;
import com.coloop.agent.capability.tool.filesystem.SearchFilesTool;
import com.coloop.agent.capability.tool.filesystem.ListDirectoryTool;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.function.Function;

public enum StandardCapability {
    EXEC_TOOL(
        "exec", "Shell执行工具", "执行shell命令，返回stdout和stderr",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new ExecTool(config.getExecTimeoutSeconds());
            }
        }
    ),
    BASE_PROMPT(
        "base_prompt", "基础提示词", "注入身份介绍、环境信息等基础系统提示",
        CapabilityType.PROMPT_PLUGIN,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new BasePromptPlugin();
            }
        }
    ),
    AGENTS_MD_PROMPT(
        "agents_md_prompt", "AGENTS.md提示词", "自动读取工作目录下的AGENTS.md并注入系统提示",
        CapabilityType.PROMPT_PLUGIN,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new AgentsMdPromptPlugin();
            }
        }
    ),
    LOGGING_HOOK(
        "logging", "日志钩子", "在Agent Loop关键生命周期节点打印调试日志",
        CapabilityType.HOOK,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new LoggingHook();
            }
        }
    ),
    READ_FILE_TOOL(
        "read_file", "文件读取工具", "读取文件内容，支持行号范围",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new ReadFileTool();
            }
        }
    ),
    WRITE_FILE_TOOL(
        "write_file", "文件写入工具", "写入新文件，若已存在则拒绝覆盖",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new WriteFileTool();
            }
        }
    ),
    EDIT_FILE_TOOL(
        "edit_file", "文件编辑工具", "基于精确字符串替换编辑文件",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new EditFileTool();
            }
        }
    ),
    SEARCH_FILES_TOOL(
        "search_files", "文件搜索工具", "使用正则表达式搜索文件内容",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new SearchFilesTool();
            }
        }
    ),
    LIST_DIRECTORY_TOOL(
        "list_directory", "目录列出工具", "列出目录中的文件和子目录",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new ListDirectoryTool();
            }
        }
    ),
    MCP_CLIENT(
        "mcp_client", "MCP 客户端", "通过 STDIO 连接 MCP Server 并暴露其工具",
        CapabilityType.TOOL,
        new Function<AppConfig, Object>() {
            @Override
            public Object apply(AppConfig config) {
                return new McpCapability(config);
            }
        }
    ),

    // todo 实现
    SKILL_PROMPT(
            "skill_prompt", "技能提示词", "扫描并注入可用技能说明",
            CapabilityType.PROMPT_PLUGIN,
            new Function<AppConfig, Object>() {
                @Override
                public Object apply(AppConfig config) {
                    return new SkillPromptPlugin();
                }
            }
    ),
    ;

    private final String id;
    private final String name;
    private final String description;
    private final CapabilityType type;
    private final Function<AppConfig, Object> factory;

    StandardCapability(String id, String name, String description, CapabilityType type, Function<AppConfig, Object> factory) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.factory = factory;
    }

    public Object create(AppConfig config) {
        return factory.apply(config);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CapabilityType getType() { return type; }

    public static StandardCapability fromId(String id) {
        for (StandardCapability c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown capability: " + id);
    }
}
