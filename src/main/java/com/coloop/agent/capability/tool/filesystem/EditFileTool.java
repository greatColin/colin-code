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
 * 基于精确字符串替换的文件编辑工具。
 */
public class EditFileTool extends BaseTool {

    @Override
    public String getName() {
        return "edit_file";
    }

    @Override
    public String getDescription() {
        return "Edit a file by replacing an exact string with a new string. The old_string must match exactly once.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "object");

        Map<String, Object> filePath = new HashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file to edit");

        Map<String, Object> oldString = new HashMap<>();
        oldString.put("type", "string");
        oldString.put("description", "Exact existing string to replace");

        Map<String, Object> newString = new HashMap<>();
        newString.put("type", "string");
        newString.put("description", "New string to replace the old string with");

        Map<String, Object> properties = new HashMap<>();
        properties.put("file_path", filePath);
        properties.put("old_string", oldString);
        properties.put("new_string", newString);

        props.put("properties", properties);
        props.put("required", java.util.Arrays.asList("file_path", "old_string", "new_string"));
        return props;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String filePath = (String) params.get("file_path");
        if (filePath == null || filePath.isEmpty()) {
            return "[Error: file_path is required]";
        }

        Object oldObj = params.get("old_string");
        Object newObj = params.get("new_string");
        if (oldObj == null) {
            return "[Error: old_string is required]";
        }
        if (newObj == null) {
            return "[Error: new_string is required]";
        }

        String oldString = oldObj.toString();
        String newString = newObj.toString();

        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return "[Error: file not found: " + path + "]";
        }
        if (!Files.isRegularFile(path)) {
            return "[Error: not a regular file: " + path + "]";
        }

        try {
            String original = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            int index = original.indexOf(oldString);
            if (index == -1) {
                return "[Error: old_string not found in file]";
            }
            int lastIndex = original.lastIndexOf(oldString);
            if (lastIndex != index) {
                return "[Error: old_string matches multiple locations in the file; replacement must be unique]";
            }

            String updated = original.substring(0, index) + newString + original.substring(index + oldString.length());
            Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
            return "File edited successfully: " + path;
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
