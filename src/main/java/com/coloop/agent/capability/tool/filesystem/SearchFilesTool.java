package com.coloop.agent.capability.tool.filesystem;

import com.coloop.agent.core.tool.BaseTool;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 基于正则表达式搜索文件内容的工具。
 */
public class SearchFilesTool extends BaseTool {

    @Override
    public String getName() {
        return "search_files";
    }

    @Override
    public String getDescription() {
        return "Search file contents using a regex pattern under a directory. Optionally filter by glob pattern. Returns up to 50 matches.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "object");

        Map<String, Object> path = new HashMap<>();
        path.put("type", "string");
        path.put("description", "Absolute directory path to search under");

        Map<String, Object> regex = new HashMap<>();
        regex.put("type", "string");
        regex.put("description", "Java regex pattern to search for");

        Map<String, Object> glob = new HashMap<>();
        glob.put("type", "string");
        glob.put("description", "Optional glob filter, e.g. '*.java' or '**/*.md'. Defaults to '**/*'");

        Map<String, Object> properties = new HashMap<>();
        properties.put("path", path);
        properties.put("regex", regex);
        properties.put("glob", glob);

        props.put("properties", properties);
        props.put("required", java.util.Arrays.asList("path", "regex"));
        return props;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String pathStr = (String) params.get("path");
        String regexStr = (String) params.get("regex");
        String glob = (String) params.get("glob");

        if (pathStr == null || pathStr.isEmpty()) {
            return "[Error: path is required]";
        }
        if (regexStr == null || regexStr.isEmpty()) {
            return "[Error: regex is required]";
        }
        if (glob == null || glob.isEmpty()) {
            glob = "**/*";
        }

        Path root = Paths.get(pathStr).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return "[Error: directory not found: " + root + "]";
        }
        if (!Files.isDirectory(root)) {
            return "[Error: not a directory: " + root + "]";
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(regexStr);
        } catch (Exception e) {
            return "[Error: invalid regex: " + e.getMessage() + "]";
        }

        String syntax = glob.startsWith("glob:") ? glob : "glob:" + glob;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntax);

        StringBuilder result = new StringBuilder();
        int matchCount = 0;
        final int MAX_MATCHES = 50;

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (matchCount >= MAX_MATCHES) {
                    break;
                }
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Path rel = root.relativize(file);
                if (!matcher.matches(rel)) {
                    continue;
                }

                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null && matchCount < MAX_MATCHES) {
                        lineNumber++;
                        Matcher m = pattern.matcher(line);
                        if (m.find()) {
                            matchCount++;
                            result.append(file.toString()).append(":").append(lineNumber).append(": ").append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    // Skip unreadable files
                }
            }
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }

        if (matchCount == 0) {
            return "No matches found.";
        }
        if (matchCount >= MAX_MATCHES) {
            result.append("(Results limited to ").append(MAX_MATCHES).append(" matches)\n");
        }
        return result.toString().trim();
    }
}
