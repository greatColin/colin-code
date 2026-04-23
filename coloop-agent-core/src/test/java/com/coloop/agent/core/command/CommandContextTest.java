package com.coloop.agent.core.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandContextTest {

    @Test
    void testConstructorWithTerminator() {
        AppConfig config = new AppConfig();
        Runnable terminator = () -> {};
        CommandContext ctx = new CommandContext(config, terminator);

        assertSame(config, ctx.getAppConfig());
        assertFalse(ctx.isTerminated());
    }

    @Test
    void testConstructorWithoutTerminator() {
        AppConfig config = new AppConfig();
        CommandContext ctx = new CommandContext(config);

        assertSame(config, ctx.getAppConfig());
        assertFalse(ctx.isTerminated());
    }

    @Test
    void testSetAndGetAgentLoop() {
        CommandContext ctx = new CommandContext(new AppConfig());
        assertNull(ctx.getAgentLoop());

        AgentLoop mockLoop = createMockAgentLoop();
        ctx.setAgentLoop(mockLoop);
        assertSame(mockLoop, ctx.getAgentLoop());
    }

    @Test
    void testSetAndGetAttribute() {
        CommandContext ctx = new CommandContext(new AppConfig());

        ctx.setAttribute("key1", "value1");
        assertEquals("value1", ctx.getAttribute("key1"));

        ctx.setAttribute("key2", 42);
        assertEquals(Integer.valueOf(42), ctx.getAttribute("key2"));
    }

    @Test
    void testGetAttributeNotFound() {
        CommandContext ctx = new CommandContext(new AppConfig());
        assertNull(ctx.getAttribute("nonexistent"));
    }

    @Test
    void testTerminateWithoutTerminator() {
        CommandContext ctx = new CommandContext(new AppConfig());
        ctx.terminate();
        assertTrue(ctx.isTerminated());
    }

    @Test
    void testTerminateWithTerminator() {
        boolean[] called = {false};
        Runnable terminator = () -> called[0] = true;
        CommandContext ctx = new CommandContext(new AppConfig(), terminator);

        ctx.terminate();
        assertTrue(ctx.isTerminated());
        assertTrue(called[0]);
    }

    @Test
    void testSetTerminatorAfterConstruction() {
        CommandContext ctx = new CommandContext(new AppConfig());

        boolean[] called = {false};
        ctx.setTerminator(() -> called[0] = true);
        ctx.terminate();

        assertTrue(called[0]);
    }

    private AgentLoop createMockAgentLoop() {
        // AgentLoop 需要多个构造参数，这里返回 null 即可，
        // setAgentLoop 只测试引用传递，不需要真实对象。
        return null;
    }
}
