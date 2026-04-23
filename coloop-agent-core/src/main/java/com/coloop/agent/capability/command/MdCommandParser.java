package com.coloop.agent.capability.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Markdown 命令解析器：从 .md 文件提取 YAML frontmatter 和提示词模板。
 *
 * <p>不依赖外部 YAML 库，手动解析简单 frontmatter（单行 key: value）。</p>
 */
public class MdCommandParser {

    public static MdCommandDefinition parse(Path path) throws IOException {
        String content = Files.readString(path);
        String filename = path.getFileName().toString();
        String defaultName = filename.endsWith(".md")
                ? filename.substring(0, filename.length() - 3)
                : filename;

        String name = defaultName;
        String description = "";
        String body = content;

        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 3) {
                String frontmatter = content.substring(3, endIdx).trim();
                body = content.substring(endIdx + 3).trim();

                for (String line : frontmatter.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int colonIdx = line.indexOf(':');
                    if (colonIdx > 0) {
                        String key = line.substring(0, colonIdx).trim();
                        String value = line.substring(colonIdx + 1).trim();
                        if ("name".equals(key)) {
                            name = value.isEmpty() ? defaultName : value;
                        } else if ("description".equals(key)) {
                            description = value;
                        }
                    }
                }
            }
        }

        return new MdCommandDefinition(name, description, body);
    }
}
