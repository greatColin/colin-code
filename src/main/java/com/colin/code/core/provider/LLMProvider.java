package com.colin.code.core.provider;

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
}
