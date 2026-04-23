package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class CommandScannerTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    void testScanNonExistentDirectory() {
        // 不应抛出异常
        assertDoesNotThrow(() ->
            CommandScanner.scanDirectory("/nonexistent/path/that/does/not/exist", registry)
        );
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void testScanFileInsteadOfDirectory(@TempDir Path tempDir) {
        // 传入文件路径而非目录
        Path file = tempDir.resolve("notadir.txt");
        assertDoesNotThrow(() -> CommandScanner.scanDirectory(file.toString(), registry));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void testScanResponseCommand(@TempDir Path tempDir) throws IOException {
        String json = "{\n" +
                "  \"name\": \"greet\",\n" +
                "  \"description\": \"Say greeting\",\n" +
                "  \"response\": \"Hello from custom command!\"\n" +
                "}";
        Files.writeString(tempDir.resolve("greet.json"), json);

        CommandScanner.scanDirectory(tempDir.toString(), registry);

        assertTrue(registry.hasCommand("greet"));
        Command cmd = registry.get("greet");
        assertEquals("greet", cmd.getName());
        assertEquals("Say greeting", cmd.getDescription());

        CommandResult result = cmd.execute(new CommandContext(new AppConfig()), "");
        assertEquals("Hello from custom command!", result.getMessage());
    }

    @Test
    void testScanResponseCommandWithoutDescription(@TempDir Path tempDir) throws IOException {
        String json = "{\n" +
                "  \"name\": \"minimal\",\n" +
                "  \"response\": \"minimal response\"\n" +
                "}";
        Files.writeString(tempDir.resolve("minimal.json"), json);

        CommandScanner.scanDirectory(tempDir.toString(), registry);

        Command cmd = registry.get("minimal");
        assertNotNull(cmd);
        assertEquals("", cmd.getDescription());
    }

    @Test
    void testScanMissingNameField(@TempDir Path tempDir) throws IOException {
        String json = "{\n" +
                "  \"description\": \"No name here\",\n" +
                "  \"response\": \"should be skipped\"\n" +
                "}";
        Files.writeString(tempDir.resolve("bad.json"), json);

        CommandScanner.scanDirectory(tempDir.toString(), registry);
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void testScanInvalidJson(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("invalid.json"), "{ not valid json");

        assertDoesNotThrow(() -> CommandScanner.scanDirectory(tempDir.toString(), registry));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void testScanMultipleCommands(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("cmd1.json"),
                "{\"name\": \"one\", \"response\": \"first\"}");
        Files.writeString(tempDir.resolve("cmd2.json"),
                "{\"name\": \"two\", \"response\": \"second\"}");
        Files.writeString(tempDir.resolve("readme.txt"),
                "this should be ignored");

        CommandScanner.scanDirectory(tempDir.toString(), registry);

        assertEquals(2, registry.getAll().size());
        assertTrue(registry.hasCommand("one"));
        assertTrue(registry.hasCommand("two"));
    }

    @Test
    void testScanDefaultUserCommandsDoesNotCrash() {
        // 默认 ~/.coloop/commands 通常不存在，不应崩溃
        assertDoesNotThrow(() -> CommandScanner.scanUserCommands(registry));
    }

    @Test
    void testScanProjectCommandsDoesNotCrash() {
        // 默认 ./.coloop/commands 通常不存在，不应崩溃
        assertDoesNotThrow(() -> CommandScanner.scanProjectCommands(registry));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void testProjectCommandsOverrideUserCommands(@TempDir Path tempDir) throws IOException {
        // 用 scanDirectory 模拟用户目录和项目目录的覆盖关系
        Path mockUserDir = tempDir.resolve("user-commands");
        Files.createDirectories(mockUserDir);
        Files.writeString(mockUserDir.resolve("shared.json"),
                "{\"name\": \"shared\", \"response\": \"from user\"}");

        Path projectDir = tempDir.resolve("project-commands");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("shared.json"),
                "{\"name\": \"shared\", \"response\": \"from project\"}");

        // 先扫用户目录
        CommandScanner.scanDirectory(mockUserDir.toString(), registry);
        // 再扫项目目录（同名覆盖）
        CommandScanner.scanDirectory(projectDir.toString(), registry);

        CommandResult result = registry.get("shared").execute(new CommandContext(new AppConfig()), "");
        assertEquals("from project", result.getMessage());
    }
}
