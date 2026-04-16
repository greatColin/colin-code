package com.colin.code.agent;

import com.colin.code.runtime.config.AppConfig;
import com.colin.code.provider.LLMProvider;
import com.colin.code.provider.LLMResponse;
import com.colin.code.provider.ToolCallRequest;
import com.colin.code.tool.ExecTool;
import com.colin.code.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * Agent 核心循环：结构清晰，只保留最关键的多轮 Chat + Tool 流程。
 *
 * 结构（高层）：
 * 1. 构造消息列表
 * 2. while 循环调用 LLM
 * 3. 如有 tool_calls -> 执行工具 -> 追加结果 -> 继续循环
 * 4. 如为纯文本 -> 返回最终结果
 *
 * 细节（私有方法）：HTTP 调用、消息追加、工具执行、超时控制等均已封装。
 */
public class AgentLoop {

    private final AppConfig config;
    private final LLMProvider provider;
    private final ContextBuilder contextBuilder;
    private final ToolRegistry toolRegistry;

    public AgentLoop(AppConfig config, LLMProvider provider) {
        this.config = config;
        this.provider = provider;
        this.contextBuilder = new ContextBuilder(config);
        this.toolRegistry = new ToolRegistry();
        registerTools();
    }

    private void registerTools() {
        toolRegistry.register(new ExecTool(config.getExecTimeoutSeconds()));
    }

    /**
     * 处理单条用户消息，返回最终回复文本。
     */
    public String chat(String userMessage) {
        List<Map<String, Object>> messages = contextBuilder.buildInitialMessages(userMessage);

        for (int iter = 0; iter < config.getMaxIterations(); iter++) {
            LLMResponse response = callLLM(messages);

            if (response.hasToolCalls()) {
                appendAssistantToolCalls(messages, response);
                executeToolsAndAppendResults(messages, response.getToolCalls());
                // 部分早期的模型需要推动一下才能继续思考，新的模型不会
                // appendReflectMessage(messages);
            } else {
                return response.getContent() != null ? response.getContent() : "";
            }
        }

        return "[Reached max iterations: " + config.getMaxIterations() + "]";
    }

    // ==================== 以下为实现细节，已封装 ====================

    private LLMResponse callLLM(List<Map<String, Object>> messages) {
        return provider.chat(
                messages,
                toolRegistry.getDefinitions(),
                config.getModel(),
                config.getMaxTokens(),
                config.getTemperature()
        );
    }

    private void appendAssistantToolCalls(List<Map<String, Object>> messages, LLMResponse response) {
        contextBuilder.addAssistantMessage(messages, response);
    }

    private void executeToolsAndAppendResults(List<Map<String, Object>> messages, List<ToolCallRequest> toolCalls) {
        for (ToolCallRequest tc : toolCalls) {
            String result = toolRegistry.execute(tc);
            contextBuilder.addToolResult(messages, tc, result);
        }
    }

    private void appendReflectMessage(List<Map<String, Object>> messages) {
        Map<String, Object> reflect = new java.util.HashMap<>();
        reflect.put("role", "user");
        reflect.put("content", "Reflect on the results and decide next steps.");
        messages.add(reflect);
    }
}
