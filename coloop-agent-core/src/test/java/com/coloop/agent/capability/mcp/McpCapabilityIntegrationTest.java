package com.coloop.agent.capability.mcp;

import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;

public class McpCapabilityIntegrationTest {

    @Test
    public void testLoadSettingConfig() throws Exception {
        InputStream is = getClass().getResourceAsStream("/coloop-agent-setting.json");
        assertNotNull(is, "coloop-agent-setting.json should be in classpath");

        ObjectMapper mapper = new ObjectMapper()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
        var root = mapper.readTree(is);
        assertNotNull(root.get("mcpServers"));
        assertNotNull(root.get("models"));
    }

    @Test
    public void testAppConfigFromSetting() throws Exception {
        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
        assertNotNull(config);
        assertNotNull(config.getMcpServers());
        assertTrue(config.getMcpServers().containsKey("MiniMax"));
        assertNotNull(config.getModelConfig("openai"));
    }

    @Test
    public void testMcpCapabilityImplementsTool() {
        AppConfig config = new AppConfig();

        McpCapability capability = new McpCapability(config);
        assertEquals("mcp_client", capability.getName());
        assertNotNull(capability.getDescription());
    }
}
