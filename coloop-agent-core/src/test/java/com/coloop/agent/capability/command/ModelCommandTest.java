package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelCommandTest {

    private final ModelCommand command = new ModelCommand();

    @Test
    void testGetName() {
        assertEquals("model", command.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(command.getDescription());
    }

    @Test
    void testExecuteWithNullAppConfig() {
        CommandContext ctx = new CommandContext(null);
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        assertEquals("No model configuration available.", result.getMessage());
    }

    @Test
    void testExecuteWithEmptyModels() {
        AppConfig config = new AppConfig();
        config.setModels(new HashMap<>());

        CommandContext ctx = new CommandContext(config);
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        assertEquals("No models configured.", result.getMessage());
    }

    @Test
    void testExecuteListsModels() {
        AppConfig config = createConfigWithModels();

        CommandContext ctx = new CommandContext(config);
        CommandResult result = command.execute(ctx, "");

        assertFalse(result.shouldTerminate());
        String msg = result.getMessage();
        assertTrue(msg.contains("Available models:"));
        assertTrue(msg.contains("gpt-4o"));
        assertTrue(msg.contains("gpt-3.5"));
        assertTrue(msg.contains("[default]"));
    }

    @Test
    void testExecuteSelectValidModel() {
        AppConfig config = createConfigWithModels();

        CommandContext ctx = new CommandContext(config);
        CommandResult result = command.execute(ctx, "gpt-3.5");

        assertFalse(result.shouldTerminate());
        assertTrue(result.getMessage().contains("gpt-3.5"));
        assertTrue(result.getMessage().contains("selected"));
    }

    @Test
    void testExecuteSelectInvalidModel() {
        AppConfig config = createConfigWithModels();

        CommandContext ctx = new CommandContext(config);
        CommandResult result = command.execute(ctx, "nonexistent");

        assertFalse(result.shouldTerminate());
        assertTrue(result.getMessage().contains("Unknown model"));
        assertTrue(result.getMessage().contains("gpt-4o"));
        assertTrue(result.getMessage().contains("gpt-3.5"));
    }

    @Test
    void testExecuteSelectWithWhitespace() {
        AppConfig config = createConfigWithModels();

        CommandContext ctx = new CommandContext(config);
        CommandResult result = command.execute(ctx, "  gpt-3.5  ");

        assertFalse(result.shouldTerminate());
        assertTrue(result.getMessage().contains("selected"));
    }

    private AppConfig createConfigWithModels() {
        AppConfig config = new AppConfig();
        Map<String, AppConfig.ModelConfig> models = new HashMap<>();

        AppConfig.ModelConfig mc1 = new AppConfig.ModelConfig();
        mc1.setModel("gpt-4o");
        mc1.setApiBase("https://api.openai.com/v1");
        models.put("gpt-4o", mc1);

        AppConfig.ModelConfig mc2 = new AppConfig.ModelConfig();
        mc2.setModel("gpt-3.5-turbo");
        mc2.setApiBase("https://api.openai.com/v1");
        models.put("gpt-3.5", mc2);

        config.setModels(models);
        config.setDefaultModel("gpt-4o");
        return config;
    }
}
