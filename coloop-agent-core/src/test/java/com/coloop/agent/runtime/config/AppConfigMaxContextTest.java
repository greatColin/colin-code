package com.coloop.agent.runtime.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigMaxContextTest {

    @Test
    void testDefaultMaxContextSizeIs100k() {
        AppConfig config = new AppConfig();
        // 没有设置 defaultModel 时，getDefaultModelConfig() 返回 null，回退到全局默认值
        assertEquals(100 * 1024, config.getMaxContextSize());
    }

    @Test
    void testParseMaxContextSizeRawNumber() {
        AppConfig config = new AppConfig();
        config.setMaxContextSize("8192");
        assertEquals(8192, config.getMaxContextSize());
    }

    @Test
    void testParseMaxContextSizeWithK() {
        AppConfig config = new AppConfig();
        config.setMaxContextSize("8k");
        assertEquals(8 * 1024, config.getMaxContextSize());
    }

    @Test
    void testParseMaxContextSizeWithM() {
        AppConfig config = new AppConfig();
        config.setMaxContextSize("4m");
        assertEquals(4 * 1024 * 1024, config.getMaxContextSize());
    }

    @Test
    void testParseMaxContextSizeWithUpperCaseK() {
        AppConfig config = new AppConfig();
        config.setMaxContextSize("200K");
        assertEquals(200 * 1024, config.getMaxContextSize());
    }

    @Test
    void testModelConfigMaxContextSizeOverridesGlobal() {
        AppConfig config = new AppConfig();
        config.setDefaultModel("minimax");

        AppConfig.ModelConfig mc = new AppConfig.ModelConfig();
        mc.setMaxContextSize("200k");
        config.getModels().put("minimax", mc);

        // 优先使用模型级别的配置
        assertEquals(200 * 1024, config.getMaxContextSize());
    }

    @Test
    void testModelConfigFallbackToGlobal() {
        AppConfig config = new AppConfig();
        config.setDefaultModel("minimax");
        config.setMaxContextSize("50k");

        AppConfig.ModelConfig mc = new AppConfig.ModelConfig();
        // 模型未配置 maxContextSize
        config.getModels().put("minimax", mc);

        // 模型未配置时，回退到全局配置
        assertEquals(50 * 1024, config.getMaxContextSize());
    }

    @Test
    void testLoadFromJsonWithModelContextSize() throws Exception {
        // 写入临时文件并通过 fromSetting 加载
        String json = "{"
                + "  \"defaultModel\": \"minimax\","
                + "  \"models\": {"
                + "    \"minimax\": { \"maxContextSize\": \"200k\" },"
                + "    \"glm\": { \"maxContextSize\": \"100k\" }"
                + "  }"
                + "}";

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test-config", ".json");
        java.nio.file.Files.writeString(tempFile, json);

        // 通过自定义类加载器让 fromSetting 能读到临时文件
        java.net.URLClassLoader tempLoader = new java.net.URLClassLoader(
                new java.net.URL[] { tempFile.getParent().toUri().toURL() },
                AppConfig.class.getClassLoader()
        );

        // 使用 ObjectMapper 直接验证 JSON 解析逻辑
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        AppConfig config = mapper.readValue(json, AppConfig.class);

        assertEquals(200 * 1024, config.getMaxContextSize());

        AppConfig.ModelConfig glm = config.getModelConfig("glm");
        assertEquals(100 * 1024, glm.getMaxContextSize());

        // 清理
        java.nio.file.Files.deleteIfExists(tempFile);
    }
}
