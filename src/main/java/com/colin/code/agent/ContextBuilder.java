package com.colin.code.agent;

import com.colin.code.runtime.config.AppConfig;
import com.colin.code.provider.LLMResponse;
import com.colin.code.provider.ToolCallRequest;
import com.colin.code.prompt.PromptSegment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文构建器：负责组装 System Prompt 和消息列表。
 *
 * 设计要点：
 * - System Prompt 由 PromptSegment 枚举拼接而成，方便对照 Claude Code 源码学习。
 * - 枚举中标记为 enabled 的段落才会注入；disabled 的段落仅作为知识库保留。
 */
public class ContextBuilder {

    private final AppConfig config;

    public ContextBuilder(AppConfig config) {
        this.config = config;
    }

    /** 构造初始消息列表：[system, userMessage] */
    public List<Map<String, Object>> buildInitialMessages(String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage());
        messages.add(userMessage(userMessage));
        return messages;
    }

    /** 追加 assistant 消息（含可能的 tool_calls） */
    public void addAssistantMessage(List<Map<String, Object>> messages, LLMResponse response) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", response.getContent() != null ? response.getContent() : "");
        if (response.hasToolCalls()) {
            msg.put("tool_calls", wrapToolCalls(response.getToolCalls()));
        }
        messages.add(msg);
    }

    /** 追加 tool 执行结果 */
    public void addToolResult(List<Map<String, Object>> messages, ToolCallRequest tc, String result) {
        Map<String, Object> tr = new HashMap<>();
        tr.put("role", "tool");
        tr.put("tool_call_id", tc.getId());
        tr.put("content", result != null ? result : "");
        messages.add(tr);
    }

    // ==================== 内部细节 ====================

    private Map<String, Object> systemMessage() {
        Map<String, Object> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", buildSystemPrompt());
        return sys;
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        // 固定头部：当前时间与工作目录（动态信息）
        sb.append("Current time: ").append(ZonedDateTime.now()).append("\n\n");

        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        sb.append("Working directory: ").append(cwd).append("\n");
        sb.append("Platform: ").append(System.getProperty("os.name")).append("\n");
        sb.append("Model: ").append(config.getModel()).append("\n\n");

        // 拼接所有启用的 PromptSegment
        for (PromptSegment segment : PromptSegment.values()) {
            if (!segment.isEnabled()) {
                continue; // 跳过未启用的段落（已在枚举中备注原因）
            }
            if (segment == PromptSegment.ENV_INFO) {
                // ENV_INFO 已在上面的固定头部中体现，这里不再重复追加
                continue;
            }
            sb.append("# ").append(segment.getTitle()).append("\n");
            sb.append(segment.getContent()).append("\n\n");
        }

        // 语言统一要求
        sb.append("Always respond in the same language as the user's message.");
        return sb.toString();
    }

    private Map<String, Object> userMessage(String content) {
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", content != null ? content : "");
        return user;
    }

    private List<Map<String, Object>> wrapToolCalls(List<ToolCallRequest> toolCalls) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolCallRequest tc : toolCalls) {
            Map<String, Object> fn = new HashMap<>();
            fn.put("id", tc.getId());
            fn.put("type", "function");
            Map<String, Object> f = new HashMap<>();
            f.put("name", tc.getName());
            try {
                f.put("arguments", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tc.getArguments()));
            } catch (Exception e) {
                f.put("arguments", "{}");
            }
            fn.put("function", f);
            out.add(fn);
        }
        return out;
    }
}
