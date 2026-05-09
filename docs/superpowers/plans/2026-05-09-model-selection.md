# 模型选择与子代理增强实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 subagent 支持可选模型选择参数、新增列出已配置模型的工具、支持 JSON 配置文件注释。

**Architecture:** 扩展 `SubagentLoopFactory` 接口增加 `modelKey` 参数；`AgentTool` 暴露可选 `model` 参数；新增 `ListModelsTool` 读取 `AppConfig` 返回模型元数据；Jackson 启用 `ALLOW_JAVA_COMMENTS` 支持配置文件注释。

**Tech Stack:** Java 21, Maven, JUnit 5, Jackson, Spring WebSocket

---

## 文件结构

| 文件 | 操作 | 说明 |
|---|---|---|
| `coloop-agent-core/src/main/java/com/coloop/agent/runtime/config/AppConfig.java` | 修改 | `ModelConfig` 增加 `description` 字段；`ObjectMapper` 启用注释支持 |
| `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentLoopFactory.java` | 修改 | 扩展 `create` 方法签名，增加 `String modelKey` 参数 |
| `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/AgentTool.java` | 修改 | 参数定义增加 `model`；`execute` 中读取并传递 `modelKey` |
| `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/ListModelsTool.java` | 创建 | 新工具：返回已配置模型列表（key/name/description/maxContextSize），隐藏敏感字段 |
| `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentManagementCapability.java` | 修改 | 构造函数增加 `AppConfig` 参数；`getTools()` 返回三个工具 |
| `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java` | 修改 | 新增 `toast(String, int)` 静态工厂方法 |
| `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java` | 修改 | 工厂闭包 lambda 扩展为 4 参数；内部根据 `modelKey` 选择 provider |
| `coloop-agent-server/src/main/resources/static/chat.js` | 修改 | `handleMessage` 增加 `toast` case；实现 `showToast` 函数 |
| `coloop-agent-core/src/main/resources/coloop-agent-setting.json` | 修改 | 为各模型添加 `description`；添加注释说明各配置项含义 |
| `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/ListModelsToolTest.java` | 创建 | 新工具单元测试 |
| `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/AgentToolTest.java` | 修改 | 更新工厂 lambda 为 4 参数；增加 `model` 参数相关测试 |
| `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/SubagentManagementCapabilityTest.java` | 修改 | 更新构造函数调用；验证 `getTools()` 返回 3 个工具 |
| `coloop-agent-server/src/test/java/com/coloop/agent/server/service/AgentServiceSubagentTest.java` | 修改 | 增加模型回退集成测试 |

---

### Task 1: AppConfig - description 字段 + JSON 注释支持

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/runtime/config/AppConfig.java`
- Test: `coloop-agent-core/src/test/java/com/coloop/agent/runtime/config/AppConfigTest.java`（如不存在则创建）

- [ ] **Step 1: Write failing test for JSON comment support**

在 `coloop-agent-core/src/test/java/com/coloop/agent/runtime/config/` 目录下创建 `AppConfigTest.java`（如不存在）：

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl coloop-agent-core -Dtest=AppConfigTest -DfailIfNoTests=false
```

Expected: 如果 `AppConfigTest` 是新创建的，`failIfNoTests=false` 会跳过。如果已有测试存在但 description 字段尚未添加，测试会因 `getDescription()` 返回空而 FAIL。

- [ ] **Step 3: Add `description` field to `ModelConfig` and enable JSON comments**

修改 `coloop-agent-core/src/main/java/com/coloop/agent/runtime/config/AppConfig.java`：

1. 导入 `JsonReadFeature`：

```java
import com.fasterxml.jackson.core.json.JsonReadFeature;
```

2. 修改 `ObjectMapper` 声明（约第 20 行）：

```java
private static final ObjectMapper MAPPER = new ObjectMapper()
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
```

3. 在 `ModelConfig` 类中增加字段和 getter/setter（在现有字段之前或之后均可）：

```java
private String description;

public String getDescription() { return description != null ? description : ""; }
public void setDescription(String description) { this.description = description; }
```

4. 在 `fromSetting()` 方法中增加 `description` 解析（在 `mc.setMaxContextSize(...)` 之前）：

```java
mc.setDescription(getString(modelNode, "description", ""));
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl coloop-agent-core -Dtest=AppConfigTest -DfailIfNoTests=false
```

Expected: PASS（前提是 `coloop-agent-setting.json` 中已为 minimax 等模型添加了 description）。如果 description 尚未添加，请先执行 Task 9 或临时在配置文件中添加 description。

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/runtime/config/AppConfig.java
git add coloop-agent-core/src/test/java/com/coloop/agent/runtime/config/AppConfigTest.java
git commit -m "feat(config): add ModelConfig.description and enable JSON comments in config files"
```

---

### Task 2: 扩展 SubagentLoopFactory 接口

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentLoopFactory.java`

- [ ] **Step 1: 修改接口签名**

将 `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentLoopFactory.java` 中的 `create` 方法扩展为 4 个参数：

```java
@FunctionalInterface
public interface SubagentLoopFactory {
    /**
     * @param name        subagent name
     * @param systemPrompt system prompt
     * @param toolNames   tool name whitelist; null means inherit all parent tools
     * @param modelKey    model config key from AppConfig; null means use main agent's provider
     * @return configured AgentLoop
     */
    AgentLoop create(String name, String systemPrompt, List<String> toolNames, String modelKey);
}
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentLoopFactory.java
git commit -m "feat(subagent): extend SubagentLoopFactory with modelKey parameter"
```

---

### Task 3: AgentTool 增加 model 参数

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/AgentTool.java`
- Test: `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/AgentToolTest.java`

- [ ] **Step 1: 修改 getParameters() 增加 model 参数定义**

在 `AgentTool.getParameters()` 中，在 `return_thinking` 参数之后添加：

```java
props.put("model", Map.of(
    "type", "string",
    "description", "Model config key (e.g. 'minimax', 'glm-4-free'). Omit to use the main agent's model."
));
```

- [ ] **Step 2: 修改 execute() 读取并传递 model 参数**

在 `AgentTool.execute()` 中，在 `boolean returnThinking = ...` 之后、在 `try` 块之前添加：

```java
String modelKey = getStringParam(params, "model");
```

然后将 `factory.create` 调用从 3 参数改为 4 参数：

```java
AgentLoop subLoop = factory.create(name, systemPrompt, toolNames, modelKey);
```

`SubagentInstance` 的构造调用不需要修改（modelKey 由工厂处理，不需要存入 instance）。

- [ ] **Step 3: 更新 AgentToolTest 中的工厂 lambda**

将所有 `(name, sp, tn) ->` 改为 `(name, sp, tn, mk) ->`。

新增一个测试验证 `model` 参数被正确传递到工厂：

```java
@Test
void testModelParameterPassedToFactory() throws IOException {
    final String[] capturedModel = new String[1];
    SubagentLoopFactory factory = (name, sp, tn, mk) -> {
        capturedModel[0] = mk;
        MockProvider provider = new MockProvider(
            List.of(new LLMResponse() {{ setContent("ok"); }})
        );
        StandardMessageBuilder mb = new StandardMessageBuilder(
            List.of(new SubagentPromptPlugin(sp)), config);
        return new AgentLoop(provider, new ToolRegistry(), mb,
            Collections.emptyList(), Collections.emptyList(), config);
    };

    AgentTool tool = new AgentTool(registry, factory);
    tool.execute(Map.of(
        "name", "m", "description", "d",
        "system_prompt", "sp", "prompt", "p",
        "model", "minimax"
    ));

    assertEquals("minimax", capturedModel[0]);
}

@Test
void testNullModelWhenOmitted() throws IOException {
    final String[] capturedModel = new String[1];
    SubagentLoopFactory factory = (name, sp, tn, mk) -> {
        capturedModel[0] = mk;
        MockProvider provider = new MockProvider(
            List.of(new LLMResponse() {{ setContent("ok"); }})
        );
        StandardMessageBuilder mb = new StandardMessageBuilder(
            List.of(new SubagentPromptPlugin(sp)), config);
        return new AgentLoop(provider, new ToolRegistry(), mb,
            Collections.emptyList(), Collections.emptyList(), config);
    };

    AgentTool tool = new AgentTool(registry, factory);
    tool.execute(Map.of(
        "name", "m", "description", "d",
        "system_prompt", "sp", "prompt", "p"
    ));

    assertNull(capturedModel[0]);
}
```

- [ ] **Step 4: 运行测试**

```bash
mvn test -pl coloop-agent-core -Dtest=AgentToolTest
```

Expected: PASS（所有 10+ 个测试通过）。

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/AgentTool.java
git add coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/AgentToolTest.java
git commit -m "feat(subagent): add optional model parameter to AgentTool"
```

---

### Task 4: 创建 ListModelsTool

**Files:**
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/ListModelsTool.java`
- Test: `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/ListModelsToolTest.java`

- [ ] **Step 1: 创建 ListModelsTool**

```java
package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.tool.BaseTool;
import com.coloop.agent.runtime.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

- [ ] **Step 2: 创建 ListModelsToolTest**

```java
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
        assertFalse(result.contains("secret"));  // apiKey should NOT be exposed
        assertFalse(result.contains("http://test"));  // apiBase should NOT be exposed
    }

    @Test
    void testExecuteWithEmptyModels() {
        ListModelsTool tool = new ListModelsTool(new AppConfig());
        String result = tool.execute(Map.of());

        assertTrue(result.contains("\"models\":[]"));
        assertTrue(result.contains("\"defaultModel\":null"));
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl coloop-agent-core -Dtest=ListModelsToolTest
```

Expected: PASS（4 个测试全部通过）。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/ListModelsTool.java
git add coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/ListModelsToolTest.java
git commit -m "feat(subagent): add ListModels tool for querying configured LLM models"
```

---

### Task 5: 更新 SubagentManagementCapability

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentManagementCapability.java`
- Test: `coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/SubagentManagementCapabilityTest.java`

- [ ] **Step 1: 修改 SubagentManagementCapability**

```java
package com.coloop.agent.capability.subagent;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.core.tool.Tool;
import com.coloop.agent.runtime.CompositeCapability;
import com.coloop.agent.runtime.config.AppConfig;
import java.util.List;

public final class SubagentManagementCapability implements CompositeCapability {

    private final SubagentRegistry registry;
    private final AgentTool agentTool;
    private final SendMessageTool sendMessageTool;
    private final ListModelsTool listModelsTool;

    public SubagentManagementCapability(SubagentLoopFactory factory,
                                         SubagentRegistry registry,
                                         SubagentEventListener listener,
                                         AppConfig config) {
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

    @Override
    public PromptPlugin getPromptPlugin() {
        return null;
    }

    @Override
    public AgentHook getHook() {
        return null;
    }

    public SubagentRegistry getRegistry() {
        return registry;
    }
}
```

- [ ] **Step 2: 更新 SubagentManagementCapabilityTest**

```java
package com.coloop.agent.capability.subagent;

import com.coloop.agent.runtime.CompositeCapability;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubagentManagementCapabilityTest {

    @Test
    void testImplementsCompositeCapability() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertTrue(cap instanceof CompositeCapability);
    }

    @Test
    void testGetToolsReturnsAllThree() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertEquals(3, cap.getTools().size());
        assertEquals("Agent", cap.getTools().get(0).getName());
        assertEquals("SendMessage", cap.getTools().get(1).getName());
        assertEquals("ListModels", cap.getTools().get(2).getName());
    }

    @Test
    void testGetPromptPluginReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertNull(cap.getPromptPlugin());
    }

    @Test
    void testGetHookReturnsNull() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertNull(cap.getHook());
    }

    @Test
    void testGetRegistryReturnsSameInstance() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentManagementCapability cap = new SubagentManagementCapability(
            (name, sp, tn, mk) -> null, registry, null, new AppConfig());
        assertSame(registry, cap.getRegistry());
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl coloop-agent-core -Dtest=SubagentManagementCapabilityTest
```

Expected: PASS（5 个测试全部通过）。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/SubagentManagementCapability.java
git add coloop-agent-core/src/test/java/com/coloop/agent/capability/subagent/SubagentManagementCapabilityTest.java
git commit -m "feat(subagent): add ListModelsTool to SubagentManagementCapability"
```

---

### Task 6: WebSocketMessage 增加 toast 工厂方法

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java`
- Test: `coloop-agent-server/src/test/java/com/coloop/agent/server/dto/WebSocketMessageTest.java`

- [ ] **Step 1: 添加 toast 工厂方法**

在 `WebSocketMessage.java` 中添加（放在其他静态工厂方法附近）：

```java
public static WebSocketMessage toast(String message, int durationMs) {
    WebSocketMessage msg = new WebSocketMessage();
    msg.type = "toast";
    msg.payload = Map.of("message", message, "durationMs", durationMs);
    return msg;
}
```

- [ ] **Step 2: 添加测试**

在 `WebSocketMessageTest.java` 中添加：

```java
@Test
void testToastMessage() {
    WebSocketMessage msg = WebSocketMessage.toast("Model not found, using default.", 5000);
    assertEquals("toast", msg.getType());
    assertEquals("Model not found, using default.", msg.getPayload().get("message"));
    assertEquals(5000, msg.getPayload().get("durationMs"));
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl coloop-agent-server -Dtest=WebSocketMessageTest
```

Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java
git add coloop-agent-server/src/test/java/com/coloop/agent/server/dto/WebSocketMessageTest.java
git commit -m "feat(websocket): add toast message factory method"
```

---

### Task 7: AgentService 工厂闭包更新

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: 修改工厂 lambda 为 4 参数并加入 model 选择逻辑**

找到 `AgentService.java` 中的工厂闭包（约第 193 行）：

将：
```java
SubagentLoopFactory factory =
    (name, sysPrompt, toolNames) -> {
```

改为：
```java
SubagentLoopFactory factory =
    (name, sysPrompt, toolNames, modelKey) -> {
        LLMProvider subProvider = provider;  // default: use main agent's provider
        if (modelKey != null && !modelKey.isEmpty()) {
            AppConfig.ModelConfig mc = config.getModelConfig(modelKey);
            if (mc != null) {
                subProvider = new OpenAICompatibleProvider(mc);
            } else {
                // Fallback: send toast notification
                try {
                    if (session.isOpen()) {
                        WebSocketMessage toast = WebSocketMessage.toast(
                            "Model '" + modelKey + "' not found in config. Using default model.",
                            5000
                        );
                        String json = objectMapper.writeValueAsString(toast);
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to send toast: " + ex.getMessage());
                }
            }
        }
```

然后将闭包内所有 `sub.build(provider, config)` 改为 `sub.build(subProvider, config)`。

- [ ] **Step 2: 修改 SubagentManagementCapability 构造调用**

找到 `AgentService.java` 中 `SubagentManagementCapability` 的构造调用（约第 254 行）：

将：
```java
SubagentManagementCapability subagentCap =
    new SubagentManagementCapability(factory, subagentRegistry, subagentListener);
```

改为：
```java
SubagentManagementCapability subagentCap =
    new SubagentManagementCapability(factory, subagentRegistry, subagentListener, config);
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl coloop-agent-server
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java
git commit -m "feat(server): support model selection in subagent factory with fallback toast"
```

---

### Task 8: 前端 toast 处理

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: 在 handleMessage 中增加 toast case**

找到 `chat.js` 中的 `handleMessage` 函数，在 `switch (msg.type)` 中增加：

```javascript
case 'toast':
    showToast(msg.payload.message, msg.payload.durationMs);
    break;
```

- [ ] **Step 2: 添加 showToast 函数**

在 `chat.js` 文件末尾（或合适的位置）添加：

```javascript
function showToast(message, durationMs) {
    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.textContent = message;
    toast.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #333;
        color: #fff;
        padding: 12px 20px;
        border-radius: 6px;
        z-index: 9999;
        font-size: 14px;
        opacity: 0;
        transition: opacity 0.3s ease;
        max-width: 400px;
        word-wrap: break-word;
    `;
    document.body.appendChild(toast);

    // Trigger fade in
    requestAnimationFrame(() => {
        toast.style.opacity = '1';
    });

    // Auto remove after duration
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }, durationMs);
}
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(frontend): add toast notification for model fallback"
```

---

### Task 9: 更新配置文件

**Files:**
- Modify: `coloop-agent-core/src/main/resources/coloop-agent-setting.json`

- [ ] **Step 1: 添加注释和 description**

将配置文件更新为带注释的版本（参考设计文档中的示例）。关键改动：

1. 在文件开头添加注释说明
2. 为每个模型添加 `description` 字段

示例改动（仅展示关键部分）：

```json
{
  // 全局默认模型，当 subagent 不指定 model 或指定了不存在的 key 时使用
  "defaultModel": "minimax",

  // 最大迭代次数，防止 agent 陷入无限循环
  "maxIterations": 50,

  // Shell 命令执行超时（秒）
  "execTimeoutSeconds": 30,

  "models": {
    /* OpenAI 兼容接口 — 需配置环境变量 */
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

- [ ] **Step 2: 运行测试验证配置解析**

```bash
mvn test -pl coloop-agent-core -Dtest=AppConfigTest
```

Expected: PASS（带注释的 JSON 能被正确解析）。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-core/src/main/resources/coloop-agent-setting.json
git commit -m "config: add model descriptions and comments to setting file"
```

---

### Task 10: 全量测试验证

- [ ] **Step 1: 运行 coloop-agent-core 全部测试**

```bash
mvn test -pl coloop-agent-core
```

Expected: BUILD SUCCESS，所有测试通过。

- [ ] **Step 2: 运行 coloop-agent-server 全部测试**

```bash
mvn test -pl coloop-agent-server
```

Expected: BUILD SUCCESS，所有测试通过。

- [ ] **Step 3: 全项目编译**

```bash
mvn compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 最终 Commit（如有未提交更改）**

```bash
git status
# 如有未提交更改则：
git add -A
git commit -m "test: verify all tests pass after model selection feature"
```

---

## Self-Review Checklist

**1. Spec coverage:**
- [x] `AgentTool` 可选 `model` 参数 → Task 3
- [x] `SubagentLoopFactory` 扩展签名 → Task 2
- [x] 模型回退 + toast 提示 → Task 7
- [x] `ListModelsTool` 创建 → Task 4
- [x] `SubagentManagementCapability` 装配 → Task 5
- [x] `WebSocketMessage.toast()` → Task 6
- [x] 前端 toast 处理 → Task 8
- [x] `ModelConfig.description` → Task 1
- [x] JSON 注释支持 → Task 1
- [x] 配置文件更新 → Task 9

**2. Placeholder scan:** 无 TBD/TODO/占位符。

**3. Type consistency:**
- `SubagentLoopFactory.create` 签名为 `(String, String, List<String>, String)` 在所有任务中一致
- `SubagentManagementCapability` 构造参数为 `(SubagentLoopFactory, SubagentRegistry, SubagentEventListener, AppConfig)` 在 Task 5 和 Task 7 中一致
- `WebSocketMessage.toast(String, int)` 签名在 Task 6 和 Task 7 中一致
