package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MdCommandIntegrationTest {

    @Test
    void testEndToEndMdCommandRegistration(@TempDir Path tempDir) throws IOException {
        Path cmdDir = tempDir.resolve("commands");
        Files.createDirectories(cmdDir);

        String md = "---\n" +
                "name: review\n" +
                "description: Review code\n" +
                "---\n" +
                "Please review the following code for quality issues: $ARGUMENTS";
        Files.writeString(cmdDir.resolve("review.md"), md);

        CommandRegistry registry = new CommandRegistry();
        registry.register(new ExitCommand());
        registry.register(new HelpCommand(registry));
        CommandScanner.scanDirectory(cmdDir.toString(), registry);

        Command help = registry.get("help");
        CommandResult helpResult = help.execute(new CommandContext(new AppConfig()), "");
        assertTrue(helpResult.getMessage().contains("review"));

        Command review = registry.get("review");
        assertNotNull(review);
        assertTrue(review instanceof MdPromptCommand);
        assertEquals("Review code", review.getDescription());
    }
}
