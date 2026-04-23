package com.coloop.agent.core.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandResultTest {

    @Test
    void testSuccessResult() {
        CommandResult result = CommandResult.success("Hello");
        assertEquals("Hello", result.getMessage());
        assertFalse(result.shouldTerminate());
    }

    @Test
    void testTerminateResult() {
        CommandResult result = CommandResult.terminate("Exited");
        assertEquals("Exited", result.getMessage());
        assertTrue(result.shouldTerminate());
    }

    @Test
    void testSuccessWithEmptyMessage() {
        CommandResult result = CommandResult.success("");
        assertEquals("", result.getMessage());
        assertFalse(result.shouldTerminate());
    }

    @Test
    void testSuccessWithMultilineMessage() {
        String message = "Line 1\nLine 2\nLine 3";
        CommandResult result = CommandResult.success(message);
        assertEquals(message, result.getMessage());
        assertFalse(result.shouldTerminate());
    }
}
