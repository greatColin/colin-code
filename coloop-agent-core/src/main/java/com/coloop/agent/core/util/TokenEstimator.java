package com.coloop.agent.core.util;

import java.util.List;
import java.util.Map;

/**
 * Token 估算器：基于字符数的轻量级估算。
 *
 * <p>精确度有限，但无需引入外部 tokenizer 库，适合上下文阈值判断。
 * 策略：中文字符按 1.5 token、非中文按 0.25 token 估算，每条消息额外加 4 token 格式开销。</p>
 */
public class TokenEstimator {

    private TokenEstimator() {}

    /**
     * 估算消息列表的总 token 数。
     */
    public static int estimate(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map<String, Object> msg : messages) {
            total += estimateMessage(msg);
        }
        return total;
    }

    /**
     * 估算单条文本的 token 数。
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chinese = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                chinese++;
            }
        }
        return (int) Math.ceil(chinese * 1.5 + (text.length() - chinese) * 0.25);
    }

    private static int estimateMessage(Map<String, Object> msg) {
        int tokens = 4; // 每条消息格式开销
        Object content = msg.get("content");
        if (content instanceof String) {
            tokens += estimate((String) content);
        }
        Object toolCalls = msg.get("tool_calls");
        if (toolCalls != null) {
            tokens += estimate(toolCalls.toString());
        }
        return tokens;
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }
}
