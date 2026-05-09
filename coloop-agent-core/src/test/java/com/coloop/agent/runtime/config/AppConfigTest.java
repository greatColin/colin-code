package com.coloop.agent.runtime.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void testJsonWithCommentsParsesSuccessfully() throws IOException {
        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
        assertNotNull(config.getDefaultModel());
        assertFalse(config.getModels().isEmpty());
    }

    @Test
    void testModelConfigDescriptionField() throws IOException {
        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
        AppConfig.ModelConfig mc = config.getModelConfig("minimax");
        assertNotNull(mc);
        assertNotNull(mc.getDescription());
        assertFalse(mc.getDescription().isEmpty());
    }
}
