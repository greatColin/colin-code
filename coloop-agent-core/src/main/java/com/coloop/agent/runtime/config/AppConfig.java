package com.coloop.agent.runtime.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 应用配置：模型参数、API 连接、执行限制等。
 * 支持从环境变量或 JSON 配置文件加载。
 */
public class AppConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAX_CONTEXT_SIZE = 100 * 1024;

    // 存储所有模型配置
    private Map<String, ModelConfig> models = new HashMap<>();

    private String defaultModel;
    private Integer maxIterations;
    private Integer execTimeoutSeconds;
    private String maxContextSize;

    // MCP 服务器配置
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();

    // ==================== 内部类：模型配置 ====================

    public static class ModelConfig {
        private static final int DEFAULT_MAX_TOKENS = 2048;
        private static final double DEFAULT_TEMPERATURE = 0.7;

        private String model;
        private String apiKey;
        private String apiBase;
        private Integer maxTokens;
        private Double temperature;
        private String maxContextSize;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getApiKey() { return apiKey != null ? apiKey : ""; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getApiBase() { return apiBase != null ? apiBase : ""; }
        public void setApiBase(String apiBase) { this.apiBase = apiBase; }

        public int getMaxTokens() { return maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature != null ? temperature : DEFAULT_TEMPERATURE; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        public boolean hasMaxContextSize() {
            return maxContextSize != null && !maxContextSize.isEmpty();
        }
        public int getMaxContextSize() {
            return AppConfig.parseMaxContextSize(maxContextSize);
        }
        public void setMaxContextSize(String maxContextSize) {
            this.maxContextSize = maxContextSize;
        }
    }

    // ==================== 内部类：MCP 服务器配置 ====================

    public static class McpServerConfig {
        private String command;
        private List<String> args;
        private Map<String, String> env;

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }

        public List<String> getArgs() { return args; }
        public void setArgs(List<String> args) { this.args = args; }

        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }

    // ==================== Getters/Setters ====================

    public Map<String, ModelConfig> getModels() { return models; }
    public void setModels(Map<String, ModelConfig> models) { this.models = models; }

    public String getDefaultModel() {
        return defaultModel;
    }
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public ModelConfig getModelConfig(String modelName) {
        return models.get(modelName);
    }

    public ModelConfig getDefaultModelConfig() {
        if (defaultModel != null && !defaultModel.isEmpty() && models.containsKey(defaultModel)) {
            return models.get(defaultModel);
        }
        return models.isEmpty() ? null : models.values().iterator().next();
    }

    public int getMaxIterations() {
        return maxIterations != null ? maxIterations : DEFAULT_MAX_ITERATIONS;
    }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }

    public int getExecTimeoutSeconds() {
        return execTimeoutSeconds != null ? execTimeoutSeconds : DEFAULT_EXEC_TIMEOUT_SECONDS;
    }
    public void setExecTimeoutSeconds(Integer execTimeoutSeconds) { this.execTimeoutSeconds = execTimeoutSeconds; }

    public int getMaxContextSize() {
        // 优先使用当前默认模型显式配置的上下文大小
        ModelConfig mc = getDefaultModelConfig();
        if (mc != null && mc.hasMaxContextSize()) {
            return mc.getMaxContextSize();
        }
        return parseMaxContextSize(maxContextSize);
    }
    public void setMaxContextSize(String maxContextSize) {
        this.maxContextSize = maxContextSize;
    }

    public Map<String, McpServerConfig> getMcpServers() { return mcpServers; }
    public void setMcpServers(Map<String, McpServerConfig> mcpServers) { this.mcpServers = mcpServers; }

    // ==================== 静态工厂方法 ====================

    /**
     * 解析最大上下文大小，支持无单位、'k'、'm' 后缀（如 8192、8k、4m）。
     */
    private static int parseMaxContextSize(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT_MAX_CONTEXT_SIZE;
        }
        String v = value.trim().toLowerCase();
        try {
            if (v.endsWith("k")) {
                return Integer.parseInt(v.substring(0, v.length() - 1).trim()) * 1024;
            } else if (v.endsWith("m")) {
                return Integer.parseInt(v.substring(0, v.length() - 1).trim()) * 1024 * 1024;
            } else {
                return Integer.parseInt(v);
            }
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CONTEXT_SIZE;
        }
    }

    /**
     * 从环境变量加载配置，返回一个新的 AppConfig 实例。
     */
    public static AppConfig fromEnv() {
        AppConfig config = new AppConfig();
        ModelConfig mc = new ModelConfig();

        String model = System.getenv("COLIN_CODE_OPENAI_MODEL");
        if (model == null) model = System.getenv("OPENAI_MODEL");

        String apiKey = System.getenv("COLIN_CODE_OPENAI_API_KEY");
        if (apiKey == null) apiKey = System.getenv("OPENAI_API_KEY");

        String apiBase = System.getenv("COLIN_CODE_OPENAI_API_BASE");
        if (apiBase == null) apiBase = System.getenv("OPENAI_API_BASE");

        if (model != null) mc.setModel(model);
        if (apiKey != null) mc.setApiKey(apiKey);
        if (apiBase != null) mc.setApiBase(apiBase);

        String maxCtx = System.getenv("COLIN_CODE_MAX_CONTEXT");
        if (maxCtx == null) maxCtx = System.getenv("MAX_CONTEXT_SIZE");
        if (maxCtx != null) config.setMaxContextSize(maxCtx);

        config.models.put("default", mc);
        return config;
    }

    /**
     * 从 JSON 配置文件加载所有配置，返回一个新的 AppConfig 实例。
     * @param resourceName 配置文件名，classpath路径
     */
    public static AppConfig fromSetting(String resourceName) throws IOException {
        InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(resourceName);
        if (is == null) {
            throw new IOException("Config file not found: " + resourceName);
        }

        JsonNode root = MAPPER.readTree(is);
        AppConfig config = new AppConfig();

        // 加载所有模型配置
        JsonNode modelsNode = root.get("models");
        if (modelsNode != null) {
            Iterator<Map.Entry<String, JsonNode>> it = modelsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String name = entry.getKey();
                JsonNode modelNode = entry.getValue();
                ModelConfig mc = new ModelConfig();
                mc.setModel(expandEnv(getString(modelNode, "model", "")));
                mc.setApiKey(expandEnv(getString(modelNode, "apiKey", "")));
                mc.setApiBase(expandEnv(getString(modelNode, "apiBase", "")));
                mc.setMaxTokens(getInteger(modelNode, "maxTokens"));
                mc.setTemperature(getDouble(modelNode, "temperature"));
                mc.setMaxContextSize(expandEnv(getString(modelNode, "maxContextSize", null)));
                config.models.put(name, mc);
            }
        }

        // 加载 MCP 服务器配置
        JsonNode mcpNode = root.get("mcpServers");
        if (mcpNode != null) {
            Iterator<Map.Entry<String, JsonNode>> it = mcpNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                McpServerConfig serverConfig = parseMcpServerConfig(entry.getValue(), modelsNode);
                config.mcpServers.put(entry.getKey(), serverConfig);
            }
        }

        // 加载全局配置项
        config.defaultModel = getString(root, "defaultModel", null);
        config.maxIterations = getInteger(root, "maxIterations");
        config.execTimeoutSeconds = getInteger(root, "execTimeoutSeconds");
        config.maxContextSize = getString(root, "maxContextSize", null);

        return config;
    }

    // ==================== 辅助方法 ====================

    private static String getString(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText(defaultValue);
        }
        return defaultValue;
    }

    private static Integer getInteger(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt();
        }
        return null;
    }

    private static Double getDouble(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble();
        }
        return null;
    }

    private static int getInt(JsonNode node, String field, int defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt(defaultValue);
        }
        return defaultValue;
    }

    private static McpServerConfig parseMcpServerConfig(JsonNode node, JsonNode modelsNode) {
        McpServerConfig config = new McpServerConfig();
        config.setCommand(getString(node, "command", ""));

        // 解析 args 数组
        if (node.has("args") && node.get("args").isArray()) {
            JsonNode argsNode = node.get("args");
            List<String> args = new ArrayList<>();
            for (int i = 0; i < argsNode.size(); i++) {
                args.add(argsNode.get(i).asText());
            }
            config.setArgs(args);
        }

        // 解析 env 对象
        if (node.has("env") && node.get("env").isObject()) {
            Map<String, String> env = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.get("env").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                env.put(entry.getKey(), expandEnvWithConfig(entry.getValue().asText(), modelsNode));
            }
            config.setEnv(env);
        }

        return config;
    }

    /**
     * 展开环境变量占位符，如 ${VAR_NAME}
     */
    private static String expandEnv(String value) {
        return expandEnvWithConfig(value, null);
    }

    /**
     * 展开占位符，支持：
     * - 环境变量：${VAR_NAME}
     * - 配置引用：${models.modelName.apiKey}
     */
    private static String expandEnvWithConfig(String value, JsonNode modelsNode) {
        if (value == null) return null;

        int maxIterations = 10; // 防止无限循环
        int iterations = 0;

        while (value.contains("${") && iterations < maxIterations) {
            iterations++;
            int start = value.indexOf("${");
            int end = value.indexOf("}", start);
            if (end < 0) break;

            String varName = value.substring(start + 2, end);
            String resolvedValue = null;

            // 支持 models.modelName.field 语法
            if (varName.startsWith("models.") && modelsNode != null) {
                String[] parts = varName.split("\\.");
                if (parts.length >= 3) {
                    String modelName = parts[1];
                    String field = parts[2];
                    if (modelsNode.has(modelName) && modelsNode.get(modelName).has(field)) {
                        resolvedValue = modelsNode.get(modelName).get(field).asText();
                    }
                }
            }

            // 回退到环境变量
            if (resolvedValue == null) {
                resolvedValue = System.getenv(varName);
            }

            if (resolvedValue != null) {
                value = value.substring(0, start) + resolvedValue + value.substring(end + 1);
            } else {
                // 未找到则移除占位符
                value = value.substring(0, start) + value.substring(end + 1);
            }
        }
        return value;
    }
}
