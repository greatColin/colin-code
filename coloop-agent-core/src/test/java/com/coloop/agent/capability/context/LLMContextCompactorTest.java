package com.coloop.agent.capability.context;

import com.coloop.agent.core.context.ConversationSummary;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMContextCompactorTest {

    @Test
    void testCompactWithKeepLastNZero() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      String model, Integer maxTokens, Double temperature) {
                // 验证传入的消息中不包含被保留的部分
                assertEquals(3, messages.size()); // system + 2 user/assistant
                LLMResponse response = new LLMResponse();
                response.setContent("摘要内容");
                return response;
            }

            @Override
            public String getDefaultModel() {
                return "mock";
            }
        };

        LLMContextCompactor compactor = new LLMContextCompactor(mockProvider, "压缩提示", 0);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put("role", "user");
        user1.put("content", "hello");
        messages.add(user1);

        Map<String, Object> assistant1 = new HashMap<>();
        assistant1.put("role", "assistant");
        assistant1.put("content", "hi");
        messages.add(assistant1);

        ConversationSummary summary = compactor.compact(messages);

        assertNotNull(summary);
        assertEquals("摘要内容", summary.getContent());
        assertEquals(2, summary.getOriginalMessageCount());
    }

    @Test
    void testCompactWithKeepLastNTwo() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      String model, Integer maxTokens, Double temperature) {
                // 只压缩前 2 条，保留后 2 条
                assertEquals(3, messages.size()); // system + 2 条被压缩的
                LLMResponse response = new LLMResponse();
                response.setContent("摘要");
                return response;
            }

            @Override
            public String getDefaultModel() {
                return "mock";
            }
        };

        LLMContextCompactor compactor = new LLMContextCompactor(mockProvider, "压缩提示", 2);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", i % 2 == 0 ? "user" : "assistant");
            msg.put("content", "msg" + i);
            messages.add(msg);
        }

        ConversationSummary summary = compactor.compact(messages);

        assertNotNull(summary);
        assertEquals(2, summary.getOriginalMessageCount());
    }

    @Test
    void testCompactTooFewMessages() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      String model, Integer maxTokens, Double temperature) {
                fail("不应被调用");
                return null;
            }

            @Override
            public String getDefaultModel() {
                return "mock";
            }
        };

        LLMContextCompactor compactor = new LLMContextCompactor(mockProvider, "提示", 2);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(new HashMap<>());

        ConversationSummary summary = compactor.compact(messages);
        assertNull(summary);
    }

    @Test
    void testCompactEmptyResponse() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse chat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      String model, Integer maxTokens, Double temperature) {
                LLMResponse response = new LLMResponse();
                response.setContent("");
                return response;
            }

            @Override
            public String getDefaultModel() {
                return "mock";
            }
        };

        LLMContextCompactor compactor = new LLMContextCompactor(mockProvider);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", "test");
        messages.add(msg);

        assertNull(compactor.compact(messages));
    }
}
