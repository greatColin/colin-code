package com.coloop.agent.capability.tool.filesystem;

import com.coloop.agent.core.tool.BaseTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 写入新文件的工具。安全策略：若文件已存在则拒绝覆盖。
 */
public class WriteFileTool extends BaseTool {

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "Write content to a new file. Fails if the file already exists to prevent accidental overwrites.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "object");

        Map<String, Object> filePath = new HashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file to create");

        Map<String, Object> content = new HashMap<>();
        content.put("type", "string");
        content.put("description", "Content to write to the file");

        Map<String, Object> properties = new HashMap<>();
        properties.put("file_path", filePath);
        properties.put("content", content);

        props.put("properties", properties);
        props.put("required", java.util.Arrays.asList("file_path", "content"));
        return props;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String filePath = (String) params.get("file_path");
        if (filePath == null || filePath.isEmpty()) {
            return "[Error: file_path is required]";
        }

        Object contentObj = params.get("content");
        if (contentObj == null) {
            return "[Error: content is required]";
        }
        String content = contentObj.toString();

        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        if (Files.exists(path)) {
            return "[Error: file already exists: " + path + "]";
        }

        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            return "File created successfully: " + path;
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
