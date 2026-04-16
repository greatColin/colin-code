package com.coloop.agent.capability.tool.filesystem;

import com.coloop.agent.core.tool.BaseTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 列出目录内容的工具。
 */
public class ListDirectoryTool extends BaseTool {

    @Override
    public String getName() {
        return "list_directory";
    }

    @Override
    public String getDescription() {
        return "List the contents of a directory, including file names, sizes, and types.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "object");

        Map<String, Object> path = new HashMap<>();
        path.put("type", "string");
        path.put("description", "Absolute path to the directory to list");

        Map<String, Object> properties = new HashMap<>();
        properties.put("path", path);

        props.put("properties", properties);
        props.put("required", Collections.singletonList("path"));
        return props;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String pathStr = (String) params.get("path");
        if (pathStr == null || pathStr.isEmpty()) {
            return "[Error: path is required]";
        }

        Path path = Paths.get(pathStr).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return "[Error: directory not found: " + path + "]";
        }
        if (!Files.isDirectory(path)) {
            return "[Error: not a directory: " + path + "]";
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<Path> entries = stream.collect(Collectors.toList());
            if (entries.isEmpty()) {
                return "Directory is empty: " + path;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Directory: ").append(path).append("\n");
            for (Path entry : entries) {
                String type = Files.isDirectory(entry) ? "DIR" : "FILE";
                long size = Files.isRegularFile(entry) ? Files.size(entry) : 0;
                sb.append(String.format("%-10s %8d bytes  %s%n", type, size, entry.getFileName().toString()));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
