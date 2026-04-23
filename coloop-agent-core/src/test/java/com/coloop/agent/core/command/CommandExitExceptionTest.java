package com.coloop.agent.core.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandExitExceptionTest {

    @Test
    void testGetExitMessage() {
        CommandExitException ex = new CommandExitException("Session ended");
        assertEquals("Session ended", ex.getExitMessage());
    }

    @Test
    void testMessageInSuperclass() {
        CommandExitException ex = new CommandExitException("Goodbye");
        assertEquals("Goodbye", ex.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        CommandExitException ex = new CommandExitException("test");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testCauseCanBeSet() {
        Throwable cause = new IllegalStateException("reason");
        CommandExitException ex = new CommandExitException("wrapped");
        // 验证它是标准的 RuntimeException，可通过 initCause 设置原因
        ex.initCause(cause);
        assertSame(cause, ex.getCause());
    }
}
