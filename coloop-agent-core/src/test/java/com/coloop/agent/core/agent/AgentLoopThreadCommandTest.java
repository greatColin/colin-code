package com.coloop.agent.core.agent;

import com.coloop.agent.core.command.CommandExitException;
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

class AgentLoopThreadCommandTest {

    /**
     * 验证：主模式下，当 AgentLoop.chat() 抛出 CommandExitException 时，
     * AgentLoopThread 将退出消息放入 resultQueue 并停止运行。
     */
    @Test
    void testMainModeCapturesCommandExitException() throws InterruptedException {
        InputInterceptor exitInterceptor = userMessage -> {
            if ("/exit".equals(userMessage.trim())) {
                throw new CommandExitException("Exited");
            }
            return Optional.empty();
        };

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                LLMResponse r = new LLMResponse();
                r.setContent("ok");
                return r;
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new AgentLoop(
                mockProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                Collections.singletonList(exitInterceptor),
                new AppConfig()
        );

        AgentLoopThread thread = new AgentLoopThread(agentLoop, AgentLoopThread.Mode.MAIN);
        thread.start();

        // 先发送一条正常消息
        thread.submit("hello");
        String result1 = thread.takeResult();
        assertEquals("ok", result1);
        assertTrue(thread.isRunning());

        // 再发送 /exit 命令
        thread.submit("/exit");
        String result2 = thread.takeResult();
        assertEquals("Exited", result2);

        // 给线程一点时间处理终止
        Thread.sleep(50);
        assertFalse(thread.isRunning());
    }

    /**
     * 验证：子模式（SUB）下，CommandExitException 被 catch (Exception e) 捕获，
     * 结果作为错误消息返回。
     */
    @Test
    void testSubModeTreatsExitAsError() throws InterruptedException {
        InputInterceptor exitInterceptor = userMessage -> {
            throw new CommandExitException("Sub exit");
        };

        LLMProvider mockProvider = new LLMProvider() {
            @Override public LLMResponse chat(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature) {
                return new LLMResponse();
            }
            @Override public String getDefaultModel() { return ""; }
            @Override public void chatStream(List<Map<String, Object>> m, List<Map<String, Object>> t, String model, Integer maxTokens, Double temperature, StreamConsumer c) {}
        };

        AgentLoop agentLoop = new AgentLoop(
                mockProvider,
                new ToolRegistry(),
                new DummyMessageBuilder(),
                Collections.emptyList(),
                Collections.singletonList(exitInterceptor),
                new AppConfig()
        );

        AgentLoopThread thread = new AgentLoopThread(agentLoop, AgentLoopThread.Mode.SUB);
        thread.start();

        thread.submit("/exit");
        String result = thread.takeResult();

        // SUB 模式下 catch(Exception) 捕获，消息前缀为 "Error: "
        assertTrue(result.contains("Sub exit"));
        thread.join();
    }

    /** 最小化的 MessageBuilder 实现 */
    private static class DummyMessageBuilder implements MessageBuilder {
        @Override public List<Map<String, Object>> buildInitial(String userMessage) {
            return new java.util.ArrayList<>();
        }
        @Override public void addUserMessage(List<Map<String, Object>> messages, String userMessage) {}
        @Override public void addAssistantMessage(List<Map<String, Object>> messages, LLMResponse response) {}
        @Override public void addToolResult(List<Map<String, Object>> messages,
                com.coloop.agent.core.provider.ToolCallRequest toolCall, String result) {}
    }
}
