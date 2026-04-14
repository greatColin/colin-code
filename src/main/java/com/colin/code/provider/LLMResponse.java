package com.colin.code.provider;

import java.util.Collections;
import java.util.List;

/**
 * LLM 对话补全响应：content、toolCalls、reasoningContent。
 */
public class LLMResponse {

    private String content;
    private List<ToolCallRequest> toolCalls;
    private String reasoningContent;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCallRequest> getToolCalls() {
        return toolCalls == null ? Collections.<ToolCallRequest>emptyList() : toolCalls;
    }

    public void setToolCalls(List<ToolCallRequest> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }
}
