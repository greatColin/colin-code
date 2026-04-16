package com.coloop.agent.capability.tool.filesystem;

import com.coloop.agent.core.tool.BaseTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取文件内容的工具，支持按行号范围读取。
 */
public class ReadFileTool extends BaseTool {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Read a file's contents. Supports optional line range via offset and limit.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "object");

        Map<String, Object> filePath = new HashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file to read");

        Map<String, Object> offset = new HashMap<>();
        offset.put("type", "integer");
        offset.put("description", "Starting line number (1-based, inclusive). Defaults to 1.");

        Map<String, Object> limit = new HashMap<>();
        limit.put("type", "integer");
        limit.put("description", "Maximum number of lines to read. Defaults to reading the entire file.");

        Map<String, Object> properties = new HashMap<>();
        properties.put("file_path", filePath);
        properties.put("offset", offset);
        properties.put("limit", limit);

        props.put("properties", properties);
        props.put("required", Collections.singletonList("file_path"));
        return props;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String filePath = (String) params.get("file_path");
        if (filePath == null || filePath.isEmpty()) {
            return "[Error: file_path is required]";
        }

        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return "[Error: file not found: " + path + "]";
        }
        if (!Files.isRegularFile(path)) {
            return "[Error: not a regular file: " + path + "]";
        }

        int offset = 1;
        int limit = -1;

        Object offsetObj = params.get("offset");
        if (offsetObj != null) {
            if (offsetObj instanceof Number) {
                offset = ((Number) offsetObj).intValue();
            } else {
                try {
                    offset = Integer.parseInt(offsetObj.toString());
                } catch (NumberFormatException e) {
                    return "[Error: offset must be an integer]";
                }
            }
        }

        Object limitObj = params.get("limit");
        if (limitObj != null) {
            if (limitObj instanceof Number) {
                limit = ((Number) limitObj).intValue();
            } else {
                try {
                    limit = Integer.parseInt(limitObj.toString());
                } catch (NumberFormatException e) {
                    return "[Error: limit must be an integer]";
                }
            }
        }

        if (offset < 1) {
            offset = 1;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return "";
            }

            int startIndex = offset - 1;
            if (startIndex >= lines.size()) {
                return "";
            }

            int endIndex = limit > 0 ? Math.min(startIndex + limit, lines.size()) : lines.size();
            StringBuilder sb = new StringBuilder();
            for (int i = startIndex; i < endIndex; i++) {
                sb.append(lines.get(i));
                if (i < endIndex - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
