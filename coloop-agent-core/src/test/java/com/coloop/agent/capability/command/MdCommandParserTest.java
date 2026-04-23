package com.coloop.agent.capability.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MdCommandParserTest {

    @Test
    void testParseFullMdCommand(@TempDir Path tempDir) throws IOException {
        String content = "---\n" +
                "name: fix\n" +
                "description: Fix code issues\n" +
                "---\n" +
                "Review the file $ARGUMENTS for bugs.\n" +
                "Fix them.";
        Path file = tempDir.resolve("fix.md");
        Files.writeString(file, content);

        MdCommandDefinition def = MdCommandParser.parse(file);

        assertEquals("fix", def.name());
        assertEquals("Fix code issues", def.description());
        assertEquals("Review the file $ARGUMENTS for bugs.\nFix them.", def.promptTemplate());
    }

    @Test
    void testParseMinimalMdCommand(@TempDir Path tempDir) throws IOException {
        String content = "---\n" +
                "name: test\n" +
                "---\n" +
                "Run the tests.";
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, content);

        MdCommandDefinition def = MdCommandParser.parse(file);

        assertEquals("test", def.name());
        assertEquals("", def.description());
        assertEquals("Run the tests.", def.promptTemplate());
    }

    @Test
    void testParseWithoutFrontmatter(@TempDir Path tempDir) throws IOException {
        String content = "Just a plain prompt without frontmatter.";
        Path file = tempDir.resolve("plain.md");
        Files.writeString(file, content);

        MdCommandDefinition def = MdCommandParser.parse(file);

        assertEquals("plain", def.name());
        assertEquals("", def.description());
        assertEquals("Just a plain prompt without frontmatter.", def.promptTemplate());
    }

    @Test
    void testParseMissingNameFallsBackToFilename(@TempDir Path tempDir) throws IOException {
        String content = "---\n" +
                "description: No name here\n" +
                "---\n" +
                "Prompt body.";
        Path file = tempDir.resolve("my-cmd.md");
        Files.writeString(file, content);

        MdCommandDefinition def = MdCommandParser.parse(file);

        assertEquals("my-cmd", def.name());
        assertEquals("No name here", def.description());
    }

    @Test
    void testParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.md");
        Files.writeString(file, "");

        MdCommandDefinition def = MdCommandParser.parse(file);

        assertEquals("empty", def.name());
        assertEquals("", def.description());
        assertEquals("", def.promptTemplate());
    }
}
