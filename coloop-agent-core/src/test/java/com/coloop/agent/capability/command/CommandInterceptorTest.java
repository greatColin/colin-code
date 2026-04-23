package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandExitException;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandInterceptorTest {

    private CommandRegistry registry;
    private CommandContext context;
    private CommandInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        context = new CommandContext(new AppConfig());
        interceptor = new CommandInterceptor(registry, context);
    }

    @Test
    void testNonSlashInputIsPassedThrough() {
        Optional<String> result = interceptor.intercept("Hello, how are you?");
        assertTrue(result.isEmpty());
    }

    @Test
    void testPlainTextWithSlashInMiddle() {
        Optional<String> result = interceptor.intercept("The price is $5/ea");
        assertTrue(result.isEmpty());
    }

    @Test
    void testUnknownCommandReturnsErrorMessage() {
        Optional<String> result = interceptor.intercept("/unknown");

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Unknown command"));
        assertTrue(result.get().contains("/unknown"));
    }

    @Test
    void testKnownCommandExecutesAndReturnsResult() {
        registry.register(new Command() {
            @Override public String getName() { return "hello"; }
            @Override public String getDescription() { return "Say hello"; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                return CommandResult.success("Hello, world!");
            }
        });

        Optional<String> result = interceptor.intercept("/hello");

        assertTrue(result.isPresent());
        assertEquals("Hello, world!", result.get());
    }

    @Test
    void testCommandWithArguments() {
        registry.register(new Command() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echo args"; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                return CommandResult.success("Echo: [" + args + "]");
            }
        });

        Optional<String> result = interceptor.intercept("/echo hello world");

        assertTrue(result.isPresent());
        assertEquals("Echo: [hello world]", result.get());
    }

    @Test
    void testCommandWithMultipleSpaces() {
        registry.register(new Command() {
            @Override public String getName() { return "trim"; }
            @Override public String getDescription() { return ""; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                return CommandResult.success("Args: [" + args + "]");
            }
        });

        Optional<String> result = interceptor.intercept("/trim   spaced   args  ");

        assertTrue(result.isPresent());
        assertEquals("Args: [spaced   args]", result.get());
    }

    @Test
    void testTerminateCommandThrowsException() {
        registry.register(new Command() {
            @Override public String getName() { return "exit"; }
            @Override public String getDescription() { return "Exit"; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                return CommandResult.terminate("Goodbye");
            }
        });

        CommandExitException ex = assertThrows(CommandExitException.class, () -> {
            interceptor.intercept("/exit");
        });

        assertEquals("Goodbye", ex.getExitMessage());
    }

    @Test
    void testTrimmedInputHandledCorrectly() {
        registry.register(new Command() {
            @Override public String getName() { return "cmd"; }
            @Override public String getDescription() { return ""; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                return CommandResult.success("OK");
            }
        });

        Optional<String> result = interceptor.intercept("  /cmd  ");
        assertTrue(result.isPresent());
        assertEquals("OK", result.get());
    }

    @Test
    void testContextPassedToCommand() {
        final CommandContext[] capturedCtx = {null};
        registry.register(new Command() {
            @Override public String getName() { return "capture"; }
            @Override public String getDescription() { return ""; }
            @Override public CommandResult execute(CommandContext ctx, String args) {
                capturedCtx[0] = ctx;
                return CommandResult.success("done");
            }
        });

        interceptor.intercept("/capture");
        assertSame(context, capturedCtx[0]);
    }
}
