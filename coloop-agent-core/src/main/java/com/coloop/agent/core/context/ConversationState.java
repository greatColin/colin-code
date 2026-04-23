package com.coloop.agent.core.context;

/**
 * 会话状态：跨组件共享的可变会话数据。
 *
 * <p>用于解耦 {@link com.coloop.agent.core.agent.AgentLoop} 与
 * {@link com.coloop.agent.core.prompt.PromptPlugin} 之间的摘要传递，
 * 避免循环依赖。</p>
 */
public class ConversationState {

    private volatile ConversationSummary summary;

    public void setSummary(ConversationSummary summary) {
        this.summary = summary;
    }

    public ConversationSummary getSummary() {
        return summary;
    }
}
