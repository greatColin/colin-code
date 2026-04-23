package com.coloop.agent.capability.context;

import com.coloop.agent.core.context.ContextCompactor;
import com.coloop.agent.core.context.ConversationSummary;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的上下文压缩器：将历史消息发给 LLM 生成摘要。
 *
 * <p>保留最近 {@code keepLastN} 轮消息不压缩，避免丢失当前任务上下文。</p>
 */
public class LLMContextCompactor implements ContextCompactor {

    private final LLMProvider provider;
    private final String summaryPrompt;
    private final int keepLastN;

    public LLMContextCompactor(LLMProvider provider) {
        this(provider, defaultPrompt(), 0);
    }

    public LLMContextCompactor(LLMProvider provider, String summaryPrompt, int keepLastN) {
        this.provider = provider;
        this.summaryPrompt = summaryPrompt != null ? summaryPrompt : defaultPrompt();
        this.keepLastN = Math.max(0, keepLastN);
    }

    @Override
    public ConversationSummary compact(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        if (messages.size() <= keepLastN) {
            return null;
        }

        int split = messages.size() - keepLastN;
        List<Map<String, Object>> toCompress = new ArrayList<>(messages.subList(0, split));

        List<Map<String, Object>> request = new ArrayList<>();
        Map<String, Object> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", summaryPrompt);
        request.add(sys);
        request.addAll(toCompress);

        LLMResponse response = provider.chat(request, null, null, null, null);
        String summaryText = response != null && response.getContent() != null
                ? response.getContent()
                : "";

        if (summaryText.isEmpty()) {
            return null;
        }

        return new ConversationSummary(
                summaryText,
                System.currentTimeMillis(),
                toCompress.size(),
                toCompress.size() / 2
        );
    }

    private static String defaultPrompt() {
        return "请对以下对话历史进行压缩摘要。摘要需要保留：\n"
                + "1. 用户的核心意图和主要请求\n"
                + "2. 已经执行的关键操作及其结果\n"
                + "3. 任何重要的代码修改、文件操作\n"
                + "4. 未完成的待办事项或待确认的问题\n"
                + "5. 关键的技术决策或结论\n\n"
                + "请用简洁的中文输出，长度控制在 500 字以内。";
    }
}
