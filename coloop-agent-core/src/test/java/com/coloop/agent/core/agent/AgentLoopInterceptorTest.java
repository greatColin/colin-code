package com.coloop.agent.core.agent;

import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.message.MessageBuilder;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.tool.ToolRegistry;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopInterceptorTest {

    /**
     * 验证：当拦截器返回 Optional.of() 时，AgentLoop.chat() 直接返回拦截结果，不调用 LLMProvider。
     */
    @Test
    void testInterceptorShortCircuitsLLMCall() {
        boolean[] providerCalled = {false};

        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools,
                                    String model, Integer maxTokens, Double temperature) {
                providerCalled[0] = true;
                LLMResponse r = new LLMResponse();
                r.setContent("mock");
                return r;
            }

            @Override
            public String getDefaultModel() { return ""; }

            @Override
            public void chatStream(List<Map<String, Object>> messages,
                                   List<Map<String, Object>> tools,
                                   String model, Integer maxTokens, Double temperature,
                                   StreamConsumer consumer) {
                providerCalled[0] = true;
            }
        };

        InputInterceptor interceptor = userMessage -> Optional.of("Intercepted: " + userMessage);

        AgentLoop agentLoop = new AgentLoop(
                mockProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                Collections.singletonList(interceptor),
                new AppConfig()
        );

        String result = agentLoop.chat("/some-command");

        assertEquals("Intercepted: /some-command", result);
        assertFalse(providerCalled[0], "Provider should NOT be called when interceptor short-circuits");
    }

    /**
     * 验证：当拦截器返回 Optional.empty() 时，消息正常传递给 LLMProvider。
     * 这里 provider 会抛异常，证明它确实被调用了。
     */
    @Test
    void testInterceptorPassThrough() {
        boolean[] providerCalled = {false};

        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools,
                                    String model, Integer maxTokens, Double temperature) {
                providerCalled[0] = true;
                LLMResponse response = new LLMResponse();
                response.setContent("Hello");
                response.setToolCalls(Collections.emptyList());
                return response;
            }

            @Override
            public String getDefaultModel() { return ""; }

            @Override
            public void chatStream(List<Map<String, Object>> messages,
                                   List<Map<String, Object>> tools,
                                   String model, Integer maxTokens, Double temperature,
                                   StreamConsumer consumer) {
                providerCalled[0] = true;
            }
        };

        InputInterceptor passThrough = userMessage -> Optional.empty();

        AgentLoop agentLoop = new AgentLoop(
                mockProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                Collections.singletonList(passThrough),
                new AppConfig()
        );

        String result = agentLoop.chat("Hello");

        assertTrue(providerCalled[0], "Provider SHOULD be called when interceptor passes through");
        assertEquals("Hello", result);
    }

    /**
     * 验证：多个拦截器按顺序执行，第一个返回 Optional.of() 的拦截器生效，后续不再执行。
     */
    @Test
    void testMultipleInterceptorsFirstMatchWins() {
        boolean[] secondInterceptorCalled = {false};

        InputInterceptor first = userMessage -> Optional.of("First wins");
        InputInterceptor second = userMessage -> {
            secondInterceptorCalled[0] = true;
            return Optional.empty();
        };

        LLMProvider noOpProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                LLMResponse r = new LLMResponse();
                r.setContent("");
                return r;
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new AgentLoop(
                noOpProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                List.of(first, second),
                new AppConfig()
        );

        String result = agentLoop.chat("anything");
        assertEquals("First wins", result);
        assertFalse(secondInterceptorCalled[0], "Second interceptor should NOT be called when first short-circuits");
    }

    /**
     * 验证：拦截器在 chatStream() 流式模式下同样生效。
     */
    @Test
    void testInterceptorShortCircuitsStreamMode() {
        boolean[] providerCalled = {false};

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                providerCalled[0] = true;
                LLMResponse r = new LLMResponse();
                r.setContent("");
                return r;
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {
                providerCalled[0] = true;
            }
        };

        InputInterceptor interceptor = userMessage -> Optional.of("Stream intercepted");

        AgentLoop agentLoop = new AgentLoop(
                mockProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                Collections.singletonList(interceptor),
                new AppConfig()
        );

        String result = agentLoop.chatStream("test", null);

        assertEquals("Stream intercepted", result);
        assertFalse(providerCalled[0], "Provider should NOT be called in stream mode when interceptor short-circuits");
    }

    /** 最小化的 MessageBuilder 实现，仅用于测试 */
    private static class DummyMessageBuilder implements MessageBuilder {
        @Override
        public List<Map<String, Object>> buildInitial(String userMessage) {
            return new java.util.ArrayList<>();
        }

        @Override
        public void addUserMessage(List<Map<String, Object>> messages, String userMessage) {}

        @Override
        public void addAssistantMessage(List<Map<String, Object>> messages, LLMResponse response) {}

        @Override
        public void addToolResult(List<Map<String, Object>> messages, com.coloop.agent.core.provider.ToolCallRequest toolCall, String result) {}
    }
}
