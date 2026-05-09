package com.coloop.agent.capability.subagent;

import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ListModelsToolTest {

    @Test
    void testGetNameReturnsListModels() {
        ListModelsTool tool = new ListModelsTool(new AppConfig());
        assertEquals("ListModels", tool.getName());
    }

    @Test
    void testGetDescriptionIsNonEmpty() {
        ListModelsTool tool = new ListModelsTool(new AppConfig());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void testExecuteReturnsJsonWithModels() {
        AppConfig config = new AppConfig();
        AppConfig.ModelConfig mc = new AppConfig.ModelConfig();
        mc.setModel("test-model");
        mc.setDescription("A test model");
        mc.setApiKey("secret");
        mc.setApiBase("http://test");
        config.getModels().put("test", mc);
        config.setDefaultModel("test");

        ListModelsTool tool = new ListModelsTool(config);
        String result = tool.execute(Map.of());

        assertTrue(result.contains("\"key\":\"test\""));
        assertTrue(result.contains("\"name\":\"test-model\""));
        assertTrue(result.contains("\"description\":\"A test model\""));
        assertTrue(result.contains("\"defaultModel\":\"test\""));
        assertFalse(result.contains("secret"));
        assertFalse(result.contains("http://test"));
    }

    @Test
    void testExecuteWithEmptyModels() {
        ListModelsTool tool = new ListModelsTool(new AppConfig());
        String result = tool.execute(Map.of());

        assertTrue(result.contains("\"models\":[]"));
        assertTrue(result.contains("\"defaultModel\":null"));
    }
}
