package com.coloop.agent.core.provider;

import java.util.List;
import java.util.Map;

/**
 * LLM 提供商接口：发起对话补全，支持 tools。
 */
public interface LLMProvider {

    /**
     * 发起对话补全。
     *
     * @param messages    消息列表（OpenAI 格式，含 role/content/tool_calls 等）
     * @param tools       工具定义（OpenAI function 格式）
     * @param model       模型名
     * @param maxTokens   最大 token 数
     * @param temperature 温度
     * @return 响应对象
     */
    LLMResponse chat(List<Map<String, Object>> messages,
                     List<Map<String, Object>> tools,
                     String model,
                     int maxTokens,
                     double temperature);

    /** 返回默认模型名 */
    String getDefaultModel();

    /**
     * 流式对话补全。默认实现退化为同步 chat() 一次性回调。
     */
    default void chatStream(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String model,
                            int maxTokens,
                            double temperature,
                            StreamConsumer consumer) {
        LLMResponse response = chat(messages, tools, model, maxTokens, temperature);
        if (response.getContent() != null && !response.getContent().isEmpty()) {
            consumer.onContent(response.getContent());
        }
        for (ToolCallRequest tc : response.getToolCalls()) {
            consumer.onToolCall(tc);
        }
        consumer.onComplete(response);
    }

    /**
     * 流式消费回调接口。
     */
    interface StreamConsumer {
        void onContent(String chunk);
        void onToolCall(ToolCallRequest toolCall);
        void onComplete(LLMResponse response);
        void onError(String error);
    }
}
