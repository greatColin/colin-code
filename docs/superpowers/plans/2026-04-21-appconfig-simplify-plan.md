# AppConfig 简化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 简化 AppConfig 配置结构，移除冗余字段，使用 static 默认值

**Architecture:** AppConfig 只保留 models/mcpServers Map，ModelConfig 内部处理默认值回退，Provider 直接接收 ModelConfig

**Tech Stack:** Java 21, Jackson, JUnit 5

---

### Task 1: 重构 ModelConfig 内部类

**Files:**
- Modify: `src/main/java/com/coloop/agent/runtime/config/AppConfig.java` (ModelConfig 部分)

- [ ] **Step 1: 修改 ModelConfig 字段类型和添加默认值**

将 ModelConfig 类中的 `maxTokens` 改为 `Integer`，`temperature` 改为 `Double`，添加 static 默认值常量，修改 getter 方法实现默认值回退：

```java
public static class ModelConfig {
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.7;

    private String model;
    private String apiKey;
    private String apiBase;
    private Integer maxTokens;
    private Double temperature;

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
}
```

---

### Task 2: 简化 AppConfig 主类

**Files:**
- Modify: `src/main/java/com/coloop/agent/runtime/config/AppConfig.java` (主类部分)

- [ ] **Step 1: 移除冗余字段和相关方法**

从 AppConfig 中移除以下内容：
- 字段：`currentModelName`、`model`、`apiKey`、`apiBase`、`maxTokens`、`temperature`
- 方法：`applyModelConfig()` 及其相关 getter/setter

- [ ] **Step 2: 添加 static 默认值和修改全局配置字段**

将 `maxIterations` 和 `execTimeoutSeconds` 改为 `Integer`，添加 static 默认值常量：

```java
public class AppConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 30;

    private Map<String, ModelConfig> models = new HashMap<>();
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();
    private Integer maxIterations;
    private Integer execTimeoutSeconds;

    // ... getters/setters ...

    public int getMaxIterations() { 
        return maxIterations != null ? maxIterations : DEFAULT_MAX_ITERATIONS; 
    }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }

    public int getExecTimeoutSeconds() { 
        return execTimeoutSeconds != null ? execTimeoutSeconds : DEFAULT_EXEC_TIMEOUT_SECONDS; 
    }
    public void setExecTimeoutSeconds(Integer execTimeoutSeconds) { this.execTimeoutSeconds = execTimeoutSeconds; }
}
```

- [ ] **Step 3: 更新 fromEnv() 方法**

```java
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
    
    config.models.put("default", mc);
    return config;
}
```

- [ ] **Step 4: 更新 fromSetting() 中的 JSON 解析**

在 `fromSetting()` 方法中，更新解析逻辑以处理 `Integer` 和 `Double` 类型：

```java
// 在 fromSetting() 方法中，更新 ModelConfig 解析部分
mc.setMaxTokens(getInteger(modelNode, "maxTokens"));
mc.setTemperature(getDouble(modelNode, "temperature"));

// 更新全局配置解析
config.maxIterations = getInteger(root, "maxIterations");
config.execTimeoutSeconds = getInteger(root, "execTimeoutSeconds");
```

- [ ] **Step 5: 添加新的辅助方法 getInteger/getDouble**

```java
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
```

---

### Task 3: 更新 OpenAICompatibleProvider

**Files:**
- Modify: `src/main/java/com/coloop/agent/capability/provider/openai/OpenAICompatibleProvider.java`

- [ ] **Step 1: 添加新构造函数，移除旧构造函数**

移除 `AppConfig config` 和 `AppConfig config, String modelName` 构造函数，添加直接接收 `ModelConfig` 的构造函数：

```java
/**
 * 从 ModelConfig 创建 Provider。
 */
public OpenAICompatibleProvider(AppConfig.ModelConfig modelConfig) {
    this.apiKey = modelConfig.getApiKey();
    this.apiBase = modelConfig.getApiBase();
    this.defaultModel = modelConfig.getModel();
    this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
}
```

---

### Task 4: 更新入口类

**Files:**
- Modify: `src/main/java/com/coloop/agent/entry/CliApp.java`
- Modify: `src/main/java/com/coloop/agent/entry/MinimalDemo.java`

- [ ] **Step 1: 更新 CliApp.java**

```java
AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
LLMProvider provider = new OpenAICompatibleProvider(config.getModelConfig("openai"));
```

- [ ] **Step 2: 更新 MinimalDemo.java**

移除 `config.applyModelConfig("openai")` 调用：

```java
AppConfig config;
try {
    config = AppConfig.fromSetting("coloop-agent-setting.json");
} catch (Exception e) {
    System.out.println("Failed to load config, using default: " + e.getMessage());
    config = AppConfig.fromEnv();
}
```

---

### Task 5: 更新测试文件

**Files:**
- Modify: `src/test/java/com/coloop/agent/capability/mcp/McpCapabilityIntegrationTest.java`

- [ ] **Step 1: 移除 applyModelConfig 调用**

```java
@Test
public void testAppConfigFromSetting() throws Exception {
    AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
    assertNotNull(config);
    assertNotNull(config.getMcpServers());
    assertTrue(config.getMcpServers().containsKey("MiniMax"));
    assertNotNull(config.getModelConfig("openai"));
}
```

---

### Task 6: 编译验证

- [ ] **Step 1: 编译项目**

Run: `mvn compile`

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试**

Run: `mvn test`

Expected: All tests pass

- [ ] **Step 3: 提交代码**

```bash
git add -A
git commit -m "refactor(config): 简化 AppConfig 结构，移除冗余字段，添加默认值支持"
```
