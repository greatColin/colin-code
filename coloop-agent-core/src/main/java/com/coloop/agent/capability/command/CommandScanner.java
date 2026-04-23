package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 命令扫描器：从指定目录加载用户自定义命令。
 *
 * <p>扫描目录下所有 {@code .json} 文件，每个文件定义一个简单命令：
 * <pre>{
 *   "name": "my-command",
 *   "description": "My custom command",
 *   "response": "Hello from custom command!"
 * }</pre>
 * 或带 shell 执行：
 * <pre>{
 *   "name": "status",
 *   "description": "Show git status",
 *   "exec": "git status"
 * }</pre>
 *
 * <p>默认扫描路径：{@code ~/.coloop/commands/}</p>
 */
public class CommandScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_COMMANDS_DIR = System.getProperty("user.home") + "/.coloop/commands";

    /**
     * 扫描默认用户命令目录（{@code ~/.coloop/commands/}）。
     */
    public static void scanUserCommands(CommandRegistry registry) {
        scanDirectory(DEFAULT_COMMANDS_DIR, registry);
    }

    /**
     * 扫描指定目录下的 JSON 命令定义文件。
     *
     * @param dirPath 目录路径
     * @param registry 要注册到的命令注册表
     */
    public static void scanDirectory(String dirPath, CommandRegistry registry) {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return;
        }

        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> loadJsonCommand(p, registry));
        } catch (IOException e) {
            System.err.println("Failed to scan commands directory " + dirPath + ": " + e.getMessage());
        }
    }

    private static void loadJsonCommand(Path path, CommandRegistry registry) {
        try {
            JsonNode node = MAPPER.readTree(path.toFile());
            if (!node.has("name")) {
                System.err.println("Command definition missing 'name' in " + path);
                return;
            }

            String name = node.get("name").asText();
            String description = node.has("description") ? node.get("description").asText() : "";
            String response = node.has("response") ? node.get("response").asText() : null;
            String exec = node.has("exec") ? node.get("exec").asText() : null;

            registry.register(new JsonDefinedCommand(name, description, response, exec));
        } catch (IOException e) {
            System.err.println("Failed to load command from " + path + ": " + e.getMessage());
        }
    }

    /**
     * 由 JSON 定义的简单命令。
     */
    private static class JsonDefinedCommand implements Command {
        private final String name;
        private final String description;
        private final String response;
        private final String exec;

        JsonDefinedCommand(String name, String description, String response, String exec) {
            this.name = name;
            this.description = description;
            this.response = response;
            this.exec = exec;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public CommandResult execute(CommandContext ctx, String args) {
            if (exec != null && !exec.isEmpty()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", exec);
                    pb.directory(new java.io.File(System.getProperty("user.dir")));
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    String output = new String(process.getInputStream().readAllBytes());
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        return CommandResult.success("[exit code " + exitCode + "]\n" + output.trim());
                    }
                    return CommandResult.success(output.trim());
                } catch (Exception e) {
                    return CommandResult.success("Error executing command: " + e.getMessage());
                }
            }
            return CommandResult.success(response != null ? response : "No response configured");
        }
    }
}
