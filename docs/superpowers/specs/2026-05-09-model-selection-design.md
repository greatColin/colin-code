# 模型选择与子代理增强设计

- **日期**：2026-05-09
- **范围**：在 `coloop-agent` 上新增三项能力：1）`AgentTool` 支持可选模型选择参数；2）新增 `ListModels` 工具供主 agent 查询已配置模型；3）JSON 配置文件支持注释。
- **设计原则**：最小改动、与现有配置体系对齐、向后兼容。

---

## 1. 用户决策回顾

| 维度 | 决策 |
|---|---|
| 模型选择参数 | `AgentTool` 增加可选 `model` 参数，值为 `AppConfig.models` 的 key |
| 模型不存在时 | 回退到主 agent 的默认模型，同时 WS 推送 `toast` 提示（5s 自动消失） |
| 模型配置描述 | `ModelConfig` 增加 `description` 字段，用于备注模型用途 |
| 列出模型工具 | 新增 `ListModels` 工具，返回 key + description + model 名，隐藏 apiKey |
| JSON 注释 | Jackson 解析器启用 `ALLOW_JAVA_COMMENTS`，支持 `//` 和 `/* */` 注释 |

---

## 2. 后端架构

### 2.1 配置文件注释支持

Jackson `ObjectMapper` 启用 `JsonReadFeature.ALLOW_JAVA_COMMENTS`：

```java
private static final ObjectMapper MAPPER = new ObjectMapper()
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
```

**影响范围**：仅 `AppConfig.fromSetting()` 中读取配置文件的 `MAPPER`。

配置文件示例（带注释）：

```json
{
  // 全局默认模型，当 subagent 不指定 model 或指定了不存在的 key 时使用
  "defaultModel": "minimax",

  // 最大迭代次数，防止 agent 陷入无限循环
  "maxIterations": 50,

  // Shell 命令执行超时（秒）
  "execTimeoutSeconds": 30,

  "models": {
    /* OpenAI 兼容接口 — 需配置环境变量 COLIN_CODE_OPENAI_API_KEY */
    "openai": {
      "apiKey": "${COLIN_CODE_OPENAI_API_KEY}",
      "apiBase": "${COLIN_CODE_OPENAI_API_BASE}",
      "model": "${COLIN_CODE_OPENAI_MODEL}"
    },
    /* 免费轻量模型，适合简单任务和探索性查询 */
    "glm-4-free": {
      "description": "免费轻量模型，适合简单任务和探索性查询",
      "apiKey": "${COLIN_CODE_GLM_API_KEY}",
      "apiBase": "https://open.bigmodel.cn/api/paas/v4",
      "model": "GLM-4.7-Flash",
      "maxContextSize": "100k"
    },
    /* 主模型 — 默认使用，能力强，适合复杂任务 */
    "minimax": {
      "description": "主模型，能力强，适合复杂任务",
      "apiKey": "${COLIN_CODE_MINIMAX_API_KEY}",
      "apiBase": "https://api.minimaxi.com/v1",
      "model": "MiniMax-M2.7",
      "maxContextSize": "200k"
    }
  }
}
```

### 2.2 `AppConfig.ModelConfig` 增加 `description`

```java
public static class ModelConfig {
    private String description;  // 新增：模型用途备注
    private String model;
    private String apiKey;
    private String apiBase;
    private Integer maxTokens;
    private Double temperature;
    private String maxContextSize;

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }
    // ... 现有 getter/setter
}
```

`fromSetting()` 中增加 `description` 字段解析：

```java
mc.setDescription(getString(modelNode, "description", ""));
```

### 2.3 `AgentTool` 增加可选 `model` 参数

**参数定义变更**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | ✓ | 子代理唯一名 |
| `description` | string | ✓ | 简述 |
| `system_prompt` | string | ✓ | 子代理 system prompt |
| `prompt` | string | ✓ | 第一条用户消息 |
| `tool_names` | array<string> | ✗ | 工具白名单 |
| `return_thinking` | boolean | ✗ | 是否返回 think 块 |
| `model` | string | ✗ | **新增**：模型配置 key（如 `"minimax"`）；不传则与主 agent 一致 |

**`SubagentLoopFactory` 接口扩展**：

```java
@FunctionalInterface
public interface SubagentLoopFactory {
    AgentLoop create(String name, String systemPrompt, List<String> toolNames, String modelKey);
}
```

> 向后兼容：如果旧代码只传 3 个参数，Java 不支持默认参数。需要同步修改所有调用点。本项目唯一调用点在 `AgentService`，无外部调用，安全。

**`AgentTool.execute` 逻辑**：

1. 从参数中读取 `model`（可能为 null）
2. 调用 `factory.create(name, systemPrompt, toolNames, model)`
3. 工厂内：
   - `modelKey` 为 null → 使用主 agent 的 provider
   - `modelKey` 非 null → `config.getModelConfig(modelKey)` 查找
     - 找到 → 用该 `ModelConfig` 新建 `OpenAICompatibleProvider`
     - 未找到 → **回退到主 agent 的 provider**，同时触发 toast 事件

**Toast 事件机制**：

工厂闭包持有 `session`（已有），在模型回退时通过 `WebSocketSession` 发送：

```java
WebSocketMessage toast = WebSocketMessage.toast(
    "Model '" + modelKey + "' not found, falling back to default model.",
    5000  // 5s
);
```

### 2.4 新增 `ListModelsTool`

```java
package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.runtime.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ListModelsTool extends BaseTool {

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public ListModelsTool(AppConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "ListModels";
    }

    @Override
    public String getDescription() {
        return "List all configured LLM models with their names, descriptions, and capabilities. " +
               "Use this to choose the right model when creating a subagent.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        params.put("required", List.of());
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (Map.Entry<String, AppConfig.ModelConfig> entry : config.getModels().entrySet()) {
            AppConfig.ModelConfig mc = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("key", entry.getKey());
            info.put("name", mc.getModel());
            info.put("description", mc.getDescription());
            info.put("maxContextSize", mc.hasMaxContextSize() ? mc.getMaxContextSize() : null);
            models.add(info);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultModel", config.getDefaultModel());
        result.put("models", models);

        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

> **安全考虑**：`apiKey`、`apiBase` 等敏感字段不返回。`maxContextSize` 返回解析后的整数值，帮助主 agent 决策上下文长度。

### 2.5 `SubagentManagementCapability` 装配变更

```java
public final class SubagentManagementCapability implements CompositeCapability {
    private final SubagentRegistry registry;
    private final AgentTool agentTool;
    private final SendMessageTool sendMessageTool;
    private final ListModelsTool listModelsTool;

    public SubagentManagementCapability(SubagentLoopFactory factory,
                                         SubagentRegistry registry,
                                         SubagentEventListener listener,
                                         AppConfig config) {  // 新增 config 参数
        this.registry = registry;
        if (listener != null) {
            this.registry.addListener(listener);
        }
        this.agentTool = new AgentTool(registry, factory);
        this.sendMessageTool = new SendMessageTool(registry);
        this.listModelsTool = new ListModelsTool(config);
    }

    @Override
    public List<Tool> getTools() {
        return List.of(agentTool, sendMessageTool, listModelsTool);
    }
    // ...
}
```

### 2.6 `AgentService` 工厂闭包变更

```java
SubagentLoopFactory factory = (name, sysPrompt, toolNames, modelKey) -> {
    LLMProvider subProvider = provider;  // 默认使用主 agent provider
    if (modelKey != null && !modelKey.isEmpty()) {
        AppConfig.ModelConfig mc = config.getModelConfig(modelKey);
        if (mc != null) {
            subProvider = new OpenAICompatibleProvider(mc);
        } else {
            // 回退 + toast
            try {
                WebSocketMessage toast = WebSocketMessage.toast(
                    "Model '" + modelKey + "' not found in config. Using default model.",
                    5000
                );
                String json = objectMapper.writeValueAsString(toast);
                session.sendMessage(new TextMessage(json));
            } catch (Exception ex) {
                System.err.println("Failed to send toast: " + ex.getMessage());
            }
        }
    }

    // ... 其余 subagent 组装逻辑不变，使用 subProvider 替代 provider
    AgentLoop subLoop = sub.build(subProvider, config);
    // ...
    return subLoop;
};
```

### 2.7 `WebSocketMessage` 新增 `toast` 工厂方法

```java
public static WebSocketMessage toast(String message, int durationMs) {
    WebSocketMessage msg = new WebSocketMessage();
    msg.type = "toast";
    msg.payload = Map.of("message", message, "durationMs", durationMs);
    return msg;
}
```

---

## 3. 前端变更

### 3.1 新增 `toast` 消息处理

在 `chat.js` 的 `handleMessage` 中增加：

```js
case 'toast':
    showToast(msg.payload.message, msg.payload.durationMs);
    break;
```

`showToast` 实现：创建 fixed 定位的 toast 元素，5s 后自动移除（CSS transition 淡出）。

---

## 4. 边界与错误处理

| 场景 | 行为 |
|---|---|
| `model` 参数缺失 | 与主 agent 使用同一 provider |
| `model` 为空字符串 | 同缺失处理 |
| `model` 指向不存在的 key | 回退到主 agent provider + WS toast 提示 |
| `ListModels` 在 `models` 为空时调用 | 返回 `{"defaultModel": null, "models": []}` |
| JSON 配置文件含非法注释语法 | Jackson 抛出解析异常，按现有错误处理 |

---

## 5. 测试策略

### 5.1 单元测试

- `AgentToolTest`：验证 `model` 参数正确传递至工厂、缺失时不传
- `ListModelsToolTest`：验证返回 JSON 包含 key/name/description、不含 apiKey
- `AppConfigTest`：验证带注释的 JSON 文件正确解析、description 字段正确读取

### 5.2 集成测试

- `AgentServiceSubagentTest`：指定有效 model key 创建 subagent，验证使用对应 provider
- `AgentServiceSubagentFallbackTest`：指定无效 model key，验证回退 + toast 事件

---

## 6. 风险与权衡

- **风险**：`SubagentLoopFactory` 接口签名变更，需要同步修改 `AgentService` 中的 lambda。无外部依赖，风险可控。
- **权衡**：Toast 回退策略比报错更宽容，但可能导致主 agent 不知道模型切换。通过 toast 提示折中。
- **权衡**：JSON 注释是非标准扩展，但 Jackson 支持良好，不影响其他 JSON 消费者。
