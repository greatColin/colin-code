package com.coloop.agent.capability.provider.mock;

import java.util.List;
import java.util.Map;

import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;

/**
 * 测试用的 Mock Provider：按预设响应列表顺序返回，便于本地验证 AgentLoop 逻辑。
 */
public class MockProvider implements LLMProvider {

    private final List<LLMResponse> responses;
    private int index = 0;

    public MockProvider(List<LLMResponse> responses) {
        this.responses = responses;
    }

    @Override
    public LLMResponse chat(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String model,
                            Integer maxTokens,
                            Double temperature) {
        if (index >= responses.size()) {
            throw new IllegalStateException("MockProvider ran out of responses at call #" + index);
        }
        return responses.get(index++);
    }

    @Override
    public String getDefaultModel() {
        return "mock-model";
    }

    public int getCallCount() {
        return index;
    }
}
