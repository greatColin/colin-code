package com.coloop.agent.capability.prompt;

import com.coloop.agent.core.context.ConversationState;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.Map;

/**
 * 摘要提示词插件：将当前会话摘要注入 system prompt。
 *
 * <p>与 {@link com.coloop.agent.core.agent.AgentLoop#compact} 配合工作：
 * 压缩完成后 AgentLoop 将摘要写入 {@link ConversationState}，
 * 本插件在生成 system prompt 时读取并追加。</p>
 */
public class SummaryPromptPlugin implements PromptPlugin {

    private ConversationState conversationState;

    public SummaryPromptPlugin() {
    }

    public void setConversationState(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    @Override
    public String getName() {
        return "summary";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String generate(AppConfig config, Map<String, Object> runtimeContext) {
        if (conversationState == null) {
            return null;
        }
        var summary = conversationState.getSummary();
        if (summary == null || summary.getContent() == null || summary.getContent().isEmpty()) {
            return null;
        }
        return "## 对话摘要\n" + summary.getContent();
    }
}
