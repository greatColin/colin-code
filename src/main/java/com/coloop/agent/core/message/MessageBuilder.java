package com.coloop.agent.core.message;

import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;

/**
 * 消息构建器：负责组装和维护 LLM 对话的消息列表。
 *
 * <p>实现类需要支持 OpenAI 风格的 message 格式（List<Map<String, Object>>），
 * 包括 system、user、assistant、tool 四种角色的消息构造。</p>
 */
public interface MessageBuilder {

    /**
     * 构造初始消息列表，通常包含 system message 和第一条 user message。
     */
    List<Map<String, Object>> buildInitial(String userMessage);

    /**
     * 向已有消息列表追加 user 消息。
     */
    void addUserMessage(List<Map<String, Object>> messages, String userMessage);

    /**
     * 向消息列表追加 assistant 消息（含可能的 tool_calls）。
     */
    void addAssistantMessage(List<Map<String, Object>> messages, LLMResponse response);

    /**
     * 向消息列表追加 tool 执行结果。
     */
    void addToolResult(List<Map<String, Object>> messages, ToolCallRequest tc, String result);
}
