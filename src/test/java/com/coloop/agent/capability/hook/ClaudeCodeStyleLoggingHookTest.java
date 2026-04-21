package com.coloop.agent.capability.hook;

import com.coloop.agent.core.provider.ToolCallRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClaudeCodeStyleLoggingHook 测试类。
 */
class ClaudeCodeStyleLoggingHookTest {

    @Test
    void testAnsiColorsConstants() {
        // 验证颜色常量不为空
        assertNotNull(AnsiColors.FG_GREEN);
        assertNotNull(AnsiColors.FG_BLUE);
        assertNotNull(AnsiColors.FG_MAGENTA);
        assertNotNull(AnsiColors.FG_YELLOW);
        assertNotNull(AnsiColors.RESET);
    }

    @Test
    void testColorize() {
        String result = AnsiColors.colorize("Hello", AnsiColors.FG_GREEN);
        assertTrue(result.startsWith(AnsiColors.FG_GREEN));
        assertTrue(result.endsWith(AnsiColors.RESET));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void testBold() {
        String result = AnsiColors.bold("text");
        assertTrue(result.startsWith(AnsiColors.BOLD));
        assertTrue(result.endsWith(AnsiColors.RESET));
    }

    @Test
    void testUnderline() {
        String result = AnsiColors.underline("text");
        assertTrue(result.startsWith(AnsiColors.UNDERLINE));
        assertTrue(result.endsWith(AnsiColors.RESET));
    }

    @Test
    void testLabel() {
        String result = AnsiColors.label("TEST", AnsiColors.FG_RED, "content", AnsiColors.FG_GREEN);
        assertTrue(result.contains("[TEST]"));
        assertTrue(result.contains("content"));
    }

    @Test
    void testClaudeLabel() {
        String result = AnsiColors.claudeLabel("THINK", "text");
        assertTrue(result.contains("[ THINK ]"));
        assertTrue(result.contains("text"));
    }

    @Test
    void testHookCreation() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        assertNotNull(hook);
    }

    @Test
    void testLoopCounting() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        
        // onLoopStart 应该重置计数
        hook.onLoopStart("test message");
        
        // beforeLLMCall 应该增加计数
        hook.beforeLLMCall(null);
        hook.beforeLLMCall(null);
        hook.beforeLLMCall(null);
        
        // 验证没有抛出异常（计数在内部管理）
        assertNotNull(hook);
    }

    @Test
    void testOnThinking() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        
        // 测试带推理内容的思考
        hook.onThinking("思考内容", "推理过程");
        
        // 测试只带思考内容
        hook.onThinking("思考内容", null);
        
        // 测试只带推理内容
        hook.onThinking(null, "推理过程");
        
        // 验证没有抛出异常
        assertNotNull(hook);
    }

    @Test
    void testOnToolCall() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        
        // 创建工具调用请求
        ToolCallRequest readFileRequest = new ToolCallRequest();
        readFileRequest.setName("ReadFileTool");
        
        // 测试带格式化参数的工具调用
        hook.onToolCall(
            readFileRequest,
            "result",
            "file_path: \"test.java\"\ncontent: \"hello\""
        );
        
        // 创建另一个工具调用请求
        ToolCallRequest simpleRequest = new ToolCallRequest();
        simpleRequest.setName("SimpleTool");
        
        // 测试不带参数的工具调用
        hook.onToolCall(
            simpleRequest,
            "result",
            null
        );
        
        // 验证没有抛出异常
        assertNotNull(hook);
    }

    @Test
    void testOnLoopEnd() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        
        // 测试正常结束
        hook.onLoopEnd(false, "最终响应内容");
        
        // 测试达到最大迭代
        hook.onLoopEnd(true, "部分响应内容");
        
        // 测试空响应
        hook.onLoopEnd(false, null);
        
        // 验证没有抛出异常
        assertNotNull(hook);
    }

    @Test
    void testFullFlow() {
        ClaudeCodeStyleLoggingHook hook = new ClaudeCodeStyleLoggingHook();
        
        // 创建工具调用请求
        ToolCallRequest readFileRequest = new ToolCallRequest();
        readFileRequest.setName("ReadFileTool");
        
        // 模拟完整的 Agent 流程
        hook.onLoopStart("请帮我读取 test.txt 文件");
        hook.beforeLLMCall(null);
        hook.onThinking("我需要先读取文件", "用户要求读取文件，所以我需要使用 ReadFileTool");
        hook.onToolCall(
            readFileRequest,
            "文件内容...",
            "file_path: \"test.txt\""
        );
        hook.beforeLLMCall(null);
        hook.onThinking("文件读取成功，现在我来总结内容", null);
        hook.onLoopEnd(false, "文件 test.txt 的内容是...");
        
        // 验证没有抛出异常
        assertNotNull(hook);
    }
}
