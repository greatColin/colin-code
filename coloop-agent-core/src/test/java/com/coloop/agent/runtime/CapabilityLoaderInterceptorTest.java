package com.coloop.agent.runtime;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.interceptor.InputInterceptor;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityLoaderInterceptorTest {

    /**
     * 验证：CapabilityLoader.withInterceptor() 能将拦截器正确组装到 AgentLoop 中。
     */
    @Test
    void testWithInterceptorAssembledInAgentLoop() {
        InputInterceptor interceptor = userMessage -> Optional.of("Intercepted");

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                LLMResponse r = new LLMResponse();
                r.setContent("Should not reach here");
                return r;
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new CapabilityLoader()
                .withInterceptor(interceptor)
                .build(mockProvider, new AppConfig());

        String result = agentLoop.chat("test");
        assertEquals("Intercepted", result);
    }

    /**
     * 验证：多个拦截器按注册顺序生效。
     */
    @Test
    void testMultipleInterceptors() {
        InputInterceptor first = userMessage -> Optional.of("First");
        InputInterceptor second = userMessage -> Optional.of("Second");

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                return new LLMResponse();
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new CapabilityLoader()
                .withInterceptor(first)
                .withInterceptor(second)
                .build(mockProvider, new AppConfig());

        // 第一个拦截器先执行并短路，所以结果是 "First"
        String result = agentLoop.chat("test");
        assertEquals("First", result);
    }

    /**
     * 验证：withInterceptor(null) 不会导致 NPE，且不影响正常流程。
     */
    @Test
    void testWithNullInterceptorIsIgnored() {
        boolean[] providerCalled = {false};

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                providerCalled[0] = true;
                LLMResponse response = new LLMResponse();
                response.setContent("From provider");
                response.setToolCalls(java.util.Collections.emptyList());
                return response;
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new CapabilityLoader()
                .withInterceptor(null)
                .build(mockProvider, new AppConfig());

        String result = agentLoop.chat("test");
        assertEquals("From provider", result);
        assertTrue(providerCalled[0]);
    }
}
