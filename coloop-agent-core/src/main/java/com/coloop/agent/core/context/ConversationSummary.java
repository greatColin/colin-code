package com.coloop.agent.core.context;

/**
 * 对话摘要：压缩历史消息后的结构化结果。
 */
public class ConversationSummary {

    private final String content;
    private final long generatedAt;
    private final int originalMessageCount;
    private final int originalTurns;

    public ConversationSummary(String content, long generatedAt, int originalMessageCount, int originalTurns) {
        this.content = content;
        this.generatedAt = generatedAt;
        this.originalMessageCount = originalMessageCount;
        this.originalTurns = originalTurns;
    }

    public String getContent() {
        return content;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public int getOriginalMessageCount() {
        return originalMessageCount;
    }

    public int getOriginalTurns() {
        return originalTurns;
    }
}
