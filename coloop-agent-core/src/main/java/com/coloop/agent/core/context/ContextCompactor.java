package com.coloop.agent.core.context;

import java.util.List;
import java.util.Map;

/**
 * 上下文压缩器：将历史消息列表压缩为摘要。
 *
 * <p>实现类负责选择压缩策略（LLM 生成、截断、过滤等），
 * 手动 /compact 命令与自动上下文压缩共用同一接口。</p>
 */
public interface ContextCompactor {

    /**
     * 对给定的消息历史进行压缩。
     *
     * @param messages 需要压缩的消息列表（通常不含 system message 和最近保留轮次）
     * @return 压缩后的摘要；若无需压缩或压缩失败，可返回 null
     */
    ConversationSummary compact(List<Map<String, Object>> messages);
}
