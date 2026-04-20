# AppConfig 简化设计

## 背景

当前 `AppConfig` 存在冗余：顶层有 `model`、`apiKey`、`apiBase`、`maxTokens`、`temperature` 字段，同时 `models` Map 里也有相同字段。`applyModelConfig()` 方法将 models 配置复制到顶层，Provider 通过顶层字段获取配置。

## 目标

- 简化配置结构，只保留 `models` 和 `mcpServers` 作为核心配置
- 使用 static 变量保存默认值，配置缺失时自动回退
- 为未来多模型切换场景做准备

## 设计

### AppConfig 简化

**移除：**
- 字段：`currentModelName`、`model`、`apiKey`、`apiBase`、`maxTokens`、`temperature`
- 方法：`applyModelConfig()`

**保留：**
- `Map<String, ModelConfig> models` — 所有模型配置
- `Map<String, McpServerConfig> mcpServers` — MCP 服务器配置
- `maxIterations`、`execTimeoutSeconds` — 全局配置

**新增 static 默认值：**
```java
public class AppConfig {
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 30;

    private Map<String, ModelConfig> models = new HashMap<>();
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();
    private Integer maxIterations;      // 改为 Integer
    private Integer execTimeoutSeconds; // 改为 Integer

    public int getMaxIterations() {
        return maxIterations != null ? maxIterations : DEFAULT_MAX_ITERATIONS;
    }

    public int getExecTimeoutSeconds() {
        return execTimeoutSeconds != null ? execTimeoutSeconds : DEFAULT_EXEC_TIMEOUT_SECONDS;
    }
}
```

### ModelConfig 增强

```java
public static class ModelConfig {
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.7;

    private String model;
    private String apiKey;
    private String apiBase;
    private Integer maxTokens;  // Integer 区分"未设置"和"设置为0"
    private Double temperature;

    public String getApiKey() {
        return apiKey != null ? apiKey : "";
    }

    public String getApiBase() {
        return apiBase != null ? apiBase : "";
    }

    public int getMaxTokens() {
        return maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    public double getTemperature() {
        return temperature != null ? temperature : DEFAULT_TEMPERATURE;
    }
}
```

### Provider 调整

**新增构造函数：**
```java
public OpenAICompatibleProvider(AppConfig.ModelConfig modelConfig) {
    this.apiKey = modelConfig.getApiKey();
    this.apiBase = modelConfig.getApiBase();
    this.defaultModel = modelConfig.getModel();
    // ...
}
```

**入口调用方式：**
```java
AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
LLMProvider provider = new OpenAICompatibleProvider(config.getModelConfig("openai"));
```

### 向后兼容

保留 `fromEnv()` 方法，用于无配置文件场景：
```java
public static AppConfig fromEnv() {
    AppConfig config = new AppConfig();
    ModelConfig mc = new ModelConfig();
    mc.setModel(System.getenv("OPENAI_MODEL"));
    mc.setApiKey(System.getenv("OPENAI_API_KEY"));
    mc.setApiBase(System.getenv("OPENAI_API_BASE"));
    config.models.put("default", mc);
    return config;
}
```

## 影响范围

- `AppConfig.java` — 主要改动
- `OpenAICompatibleProvider.java` — 调整构造函数
- `CliApp.java` / `MinimalDemo.java` — 调整调用方式
- 测试文件 — 相应更新
