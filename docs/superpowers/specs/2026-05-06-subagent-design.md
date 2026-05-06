# Subagent 设计（命名子代理 + 聊天式侧栏）

- **日期**：2026-05-06
- **范围**：在 `coloop-agent` 上新增"命名子代理"能力，主代理通过工具调用创建/向子代理追加消息；前端左侧栏从任务列表改为代理切换器，支持查看主代理与各子代理的完整对话。
- **设计原则**：优先与 Claude Code 对齐；严格遵守现有"core-capability-runtime-entry"洋葱分层；最大化复用 `AgentLoop` 与现有前端渲染。

---

## 1. 用户决策回顾

| 维度 | 决策 |
|---|---|
| 执行模型 | 阻塞式工具调用（与 Claude Code Agent 一致） |
| 子代理工具集 | `tool_names` 可选数组；不传则默认 = 主代理工具集 - {Agent, SendMessage} |
| 子代理记忆 | 独立、跨调用持久；每个子代理自带 `ContextCompactor`，达阈值自动压缩 |
| 子代理 system prompt | 由 `Agent` 调用方内联传入（不继承主代理 prompt 链） |
| 同名替换 | `Agent` 调用同名子代理时，先清空旧实例（含 UI），再建新实例 |
| 左侧栏 | 完全替换为 Agent 列表（任务列表 UI 移除；后端任务能力保留） |
| 子代理聊天页粒度 | 完整内部 loop（thinking、tool_call、tool_result、stream_chunk） |
| 实时同步 | WebSocket 流式推送，按 `agentName` 路由到对应面板 |
| 工具命名 | `Agent` / `SendMessage`（与 Claude Code 对齐） |

---

## 2. 后端架构

### 2.1 新增模块

```
coloop-agent-core/src/main/java/com/coloop/agent/capability/subagent/
├── SubagentRegistry.java
├── SubagentInstance.java
├── SubagentLoopFactory.java
├── SubagentEventListener.java
├── SubagentPromptPlugin.java
├── AgentTool.java                    // 工具名:"Agent"
├── SendMessageTool.java              // 工具名:"SendMessage"
└── SubagentManagementCapability.java // CompositeCapability

coloop-agent-server/src/main/java/com/coloop/agent/server/hook/
└── SubagentLoggingHook.java          // 包装 WebSocketLoggingHook,事件附 agentName
```

### 2.2 数据模型

**`SubagentInstance`**

```java
public final class SubagentInstance {
    public final String name;
    public final String description;
    public final String systemPrompt;
    public final List<String> toolNames;
    public final AgentLoop agentLoop;
    public final long createdAt;
    public volatile boolean running;       // 当前是否在 chatStream 中
    public final Object runLock = new Object();
}
```

**`SubagentRegistry`**

```java
public final class SubagentRegistry {
    private final ConcurrentMap<String, SubagentInstance> map = new ConcurrentHashMap<>();
    private final List<SubagentEventListener> listeners = new CopyOnWriteArrayList<>();

    /** 创建或替换:同名旧实例先 dispose 并触发 onCleared,再 put 新实例并触发 onCreated */
    public void createOrReplace(String name, SubagentInstance instance);

    public SubagentInstance get(String name);
    public void remove(String name);
    public List<SubagentInstance> snapshot();
    public void clear();    // /new-session 时调用
    public void addListener(SubagentEventListener l);
}
```

**`SubagentEventListener`**

```java
public interface SubagentEventListener {
    void onCreated(SubagentInstance inst);
    void onCleared(String name);
}
```

**`SubagentLoopFactory`**

```java
@FunctionalInterface
public interface SubagentLoopFactory {
    /** 由 server 层注入,闭包持有 LLMProvider/AppConfig/parentTools/sessionHook 工厂等 session 依赖 */
    AgentLoop create(String name, String systemPrompt, List<String> toolNames);
}
```

**`SubagentPromptPlugin`**：极简 PromptPlugin，仅把传入的 `systemPrompt` 作为单条系统提示注入；不带 BasePromptPlugin/AgentsMd/Skill 这类继承内容（与"独有 prompt"决策一致）。

### 2.3 SubagentManagementCapability（Composite）

```java
public final class SubagentManagementCapability implements CompositeCapability {
    private final SubagentRegistry registry;
    private final AgentTool agentTool;
    private final SendMessageTool sendMessageTool;

    public SubagentManagementCapability(SubagentLoopFactory factory,
                                        SubagentEventListener listener) {
        this.registry = new SubagentRegistry();
        this.registry.addListener(listener);
        this.agentTool = new AgentTool(registry, factory);
        this.sendMessageTool = new SendMessageTool(registry);
    }

    @Override public List<Tool> getTools() { return List.of(agentTool, sendMessageTool); }
    @Override public PromptPlugin getPromptPlugin() { return null; }
    @Override public AgentHook getHook() { return null; }

    public SubagentRegistry getRegistry() { return registry; }
}
```

`StandardCapability` 不新增枚举（需要外部注入依赖）；通过 `CapabilityLoader.withComposite(...)` 装配。

### 2.4 工具定义

**`Agent`**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | ✓ | 子代理唯一名（同名清空替换）|
| `description` | string | ✓ | 简述（侧栏显示用）|
| `system_prompt` | string | ✓ | 子代理 system prompt |
| `prompt` | string | ✓ | 第一条用户消息，创建后立即触发一次 loop |
| `tool_names` | array&lt;string&gt; | ✗ | 工具名白名单；不传则用默认子集 |

执行流程：
1. 校验参数（缺失/空字符串返回 `Error: missing required field 'X'`）
2. `factory.create(name, system_prompt, tool_names)` 拿到 AgentLoop
3. `registry.createOrReplace(name, new SubagentInstance(...))`（同名先清空）
4. 在 `runLock` 内 `agentLoop.chatStream(prompt, …)`
5. 返回最终响应字符串

**`SendMessage`**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `to` | string | ✓ | 已存在子代理名 |
| `message` | string | ✓ | 追加的用户消息 |
| `summary` | string | ✗ | 5–10 词预览（侧栏显示用，可选）|

执行流程：
1. `inst = registry.get(to)`；不存在返回 `Error: subagent '<to>' not found`
2. `runLock` 内 `inst.agentLoop.chatStream(message, …)`
3. 返回最终响应字符串

**递归保护**：`AgentTool.execute` 内部把传入的 `tool_names` 自动剔除 `Agent` 与 `SendMessage`。即使主代理误传也不会让子代理获得这两个工具。

### 2.5 AgentService 装配（server）

`AgentService.startChat` 中，主代理 CapabilityLoader 装配前先构造 `SubagentLoopFactory` 闭包：

```java
SubagentLoopFactory factory = (name, sysPrompt, toolNames) -> {
    List<Tool> filtered = filterTools(parentTools, toolNames);
    SubagentLoggingHook subHook = new SubagentLoggingHook(session, name);
    CapabilityLoader sub = new CapabilityLoader()
        .withMessageBuilder(new StandardMessageBuilder(
            List.of(new SubagentPromptPlugin(sysPrompt)), config))
        .withHook(subHook);
    for (Tool t : filtered) sub.withTool(t);
    AgentLoop subLoop = sub.build(provider, config);
    subHook.setAgentLoop(subLoop);
    return subLoop;
};

SubagentEventListener listener = (inst) -> sendCreated(session, inst);
                                 // onCleared 类似
SubagentManagementCapability subagentCap = new SubagentManagementCapability(factory, listener);
```

主代理装配链增加 `.withComposite(subagentCap)`。

### 2.6 与 `/new-session` 等命令的协同

`NewSessionCommand` 触发 `agentLoop.reset()` 时，AgentService 在同一事务里调用 `subagentCap.getRegistry().clear()`，逐个触发 `onCleared` → 前端清空所有子代理面板。

---

## 3. WebSocket 协议变更

### 3.1 `WebSocketMessage` 增字段

```java
public class WebSocketMessage {
    private String type;
    private String agentName;        // 新增,默认 null = 视为 "main"
    private Map<String,Object> payload;
    private long timestamp;

    /** 链式补设 agentName,用于子代理事件 */
    public WebSocketMessage withAgent(String name) { this.agentName = name; return this; }
}
```

现有静态工厂方法均不变，向后兼容（主代理事件 `agentName=null`，前端按 `"main"` 处理）。

### 3.2 新增消息类型

| type | payload | 何时发送 |
|---|---|---|
| `subagent_created` | `{name, description, summary?}` | `SubagentRegistry.onCreated` |
| `subagent_cleared` | `{name}` | `SubagentRegistry.onCleared` |

### 3.3 `SubagentLoggingHook`

实现 `AgentHook`，构造时持有 `WebSocketSession + agentName`。所有方法体把要发送的 `WebSocketMessage` 调 `.withAgent(agentName)` 后再 `session.sendMessage(...)`。其他逻辑（context_usage、stream_chunk 等）与 `WebSocketLoggingHook` 一致——可考虑抽公共基类避免重复。

### 3.4 移除/保留

- 子代理事件 **不发** `task_list` / `task_update`（任务管理是主代理独有能力，子代理默认不带 `task_management` 工具集）
- 主代理 `task_list` / `task_update` 推送**保留**到协议层（避免破坏 `TaskManagementCapability`），但前端不再渲染任务侧栏，消息被丢弃

---

## 4. 前端改造（`coloop-agent-server/src/main/resources/static/`）

### 4.1 `index.html` 结构改动

替换 `<aside class="task-sidebar">` 为：

```html
<aside class="agent-sidebar" id="agent-sidebar">
  <div class="agent-sidebar-header">
    <span class="agent-sidebar-title">🧠 Agents</span>
    <span class="agent-sidebar-toggle" id="agent-sidebar-toggle">◀</span>
  </div>
  <div class="agent-list" id="agent-list">
    <div class="agent-item active" data-agent="main">
      <span class="agent-icon">⭐</span>
      <span class="agent-name">main</span>
    </div>
    <!-- 子代理动态插入 -->
  </div>
</aside>
```

CSS：复用现有 task-sidebar 的样式骨架（侧栏宽度、折叠交互），重命名类名；`agent-item` 视觉与 `task-item` 类似，加 `.active` 高亮当前选中。

### 4.2 `chat.js` 改造

**关键设计**：`chatContainer` 始终只渲染当前激活 agent 的消息；非激活 agent 的 DOM 用 `DocumentFragment` 暂存。

```js
const agentBuffers = new Map();           // agentName -> { fragment, agentMeta }
let currentAgent = 'main';
agentBuffers.set('main', { fragment: document.createDocumentFragment(), meta: { name:'main' } });

function getActiveContainer() {
    return chatContainer;   // 只对 currentAgent 直接 append;其他写入 buffer
}

function appendToAgent(agentName, el) {
    if (agentName === currentAgent) {
        chatContainer.appendChild(el);
    } else {
        agentBuffers.get(agentName).fragment.appendChild(el);
    }
}
```

所有现有渲染函数（`renderUser`、`renderToolCall`、`renderToolResult`、`renderThinking`、`appendStreamChunk`、`finalizeAssistant`、`renderLoopStart`、`renderSystem`、`renderError`）签名不变，内部调用 `appendElement(...)` 与 `insertBeforeAssistant(...)` 改为接收 `agentName` 参数（默认 main），内部走 `appendToAgent`。

**消息路由**：

```js
function handleMessage(msg) {
    const agent = msg.agentName || 'main';
    switch (msg.type) {
        case 'subagent_created':
            addAgentToSidebar(msg.payload);
            agentBuffers.set(msg.payload.name, { fragment: document.createDocumentFragment(), meta: msg.payload });
            return;
        case 'subagent_cleared':
            removeAgentFromSidebar(msg.payload.name);
            agentBuffers.delete(msg.payload.name);
            if (currentAgent === msg.payload.name) switchToAgent('main');
            return;
        // ...其他 type 走原有处理,但注意要把 agent 传入渲染函数...
    }
    renderAgentMessage(agent, msg);
}
```

**切换 agent**：

```js
function switchToAgent(name) {
    if (name === currentAgent) return;
    // 1. 把 chatContainer 当前的 children 移回 currentAgent 的 fragment
    const currentBuf = agentBuffers.get(currentAgent);
    while (chatContainer.firstChild) currentBuf.fragment.appendChild(chatContainer.firstChild);
    // 2. 把目标 fragment 的 children 装入 chatContainer
    const targetBuf = agentBuffers.get(name);
    while (targetBuf.fragment.firstChild) chatContainer.appendChild(targetBuf.fragment.firstChild);
    // 3. 更新高亮
    currentAgent = name;
    updateSidebarActive(name);
    scrollToBottom();
}
```

**侧栏点击绑定**：`agent-list` 上委托 `click` 事件，dataset.agent 取目标名调 `switchToAgent`。

**保留功能**：command-suggestions（与 agent 无关）、context-bar（始终显示当前激活 agent 的占用——`context_usage` 也带 agentName，前端按 currentAgent 切换显示数据）、theme-switcher 不变。

**移除**：`task-sidebar`、`renderTaskList`、`updateTaskStatus`、`taskListEl` 等任务相关 DOM 与 JS（保留 `task_list` / `task_update` case 为 no-op 防御，避免后端发来时报错）。

### 4.3 子代理被清空时的 UX

- sidebar 该 agent 项淡出移除（CSS transition）
- 若用户当前正看该 agent，自动切回 main 并提示一行 system 消息：`Subagent 'planner' was cleared.`

---

## 5. 边界与错误处理

| 场景 | 行为 |
|---|---|
| `Agent` 参数缺失/空 | 工具返回 `Error: missing required field '<x>'` |
| `Agent` `tool_names` 含未知工具名 | 静默忽略未知项；若过滤后为空则返回 `Error: tool_names resulted in empty toolset` |
| `SendMessage` 找不到目标 | 返回 `Error: subagent '<to>' not found` |
| 子代理 loop 抛异常 | 异常 message 作为工具返回 `Error: <message>`，主代理可决定继续 |
| WebSocketSession 断开期间子代理在跑 | hook 内 `session.isOpen()` 检查跳过发送，不抛 |
| `/new-session` | `subagentCap.registry.clear()`，逐个 `onCleared` |
| `/stop` | 仅停主代理（与现状一致）；子代理由其各自 AgentLoop 内的 stopRequested 控制——本 spec 不暴露子代理级 stop 工具，未来可加 |
| 同名 `Agent` 调用并发（极端） | `createOrReplace` 内部 `synchronized`，串行执行 |
| 子代理工具集自传 `Agent`/`SendMessage` | `AgentTool.execute` 内强制剔除 |

---

## 6. 测试策略

### 6.1 单元测试（`coloop-agent-core/src/test/.../capability/subagent/`）

- `SubagentRegistryTest`：put/get/createOrReplace 触发 cleared、并发安全（多线程 createOrReplace 同名，验证最终唯一）
- `SubagentInstanceTest`：构造与字段不变性
- `AgentToolTest`：用 MockProvider 验证创建子代理、参数缺失返回 Error、工具集过滤
- `SendMessageToolTest`：验证 to 不存在报错、复用历史（第二次调用消息历史中含第一次的内容）
- `SubagentManagementCapabilityTest`：Composite 装配，getTools 返回两个

### 6.2 集成测试（`coloop-agent-server/src/test/`）

- `AgentServiceSubagentTest`：模拟 `WebSocketSession`，主代理 chatStream 调 `Agent` 工具，断言 `subagent_created` 事件、子代理 thinking/tool_call 事件 `agentName` 字段正确
- `AgentServiceSameNameClearTest`：连续两次 `Agent` 同名，断言收到一次 `subagent_cleared` + 两次 `subagent_created`

### 6.3 前端手测

- 启动 server，发"创建一个名叫 planner 的子代理研究 X"
- 观察侧栏出现 planner 条目；切到 planner 看到完整内部 loop
- 切回 main 看到 `Agent` tool_call 卡片 + 工具结果
- 再发"用 SendMessage 让 planner 改成 Y"，验证 planner 面板追加新轮次
- 发"再创建一个 planner 子代理重做"，验证旧 planner 面板被清空

---

## 7. 未来扩展（不在本次范围）

- **子代理类型 profile**：支持 `.coloop/agents/<type>.md`，`Agent` 工具增加 `subagent_type` 参数从文件加载 system_prompt + 默认 tool_names（与 Claude Code `subagent_type` 对齐）
- **子代理后台执行**：`Agent` 增加 `run_in_background=true` 参数，启动后台 loop，主代理可继续；配套加 `wait_subagent` / `kill_subagent` 工具
- **子代理之间通信**：当前子代理拿不到 SendMessage（被剔除），可放开使其能互相发消息
- **CLI 入口**：`MinimalDemo` 中暴露子代理能力（CLI 用文本块切换显示当前 agent）
- **任务侧栏融合**：在 agent 详情页底部展示该 agent 当前任务，恢复任务可见性

---

## 8. 风险与权衡

- **风险**：前端 buffer 切换若与流式渲染交叉发生（用户在子代理刚开始流式输出时点回 main），需保证写入仍走 fragment 而非可见 chatContainer。`appendStreamChunk` 内部通过 `agentName !== currentAgent` 分支路由到 fragment 解决。
- **权衡**：未引入"agent profile 文件"机制——保持简单；将来若要复用 prompt 配置，再加 subagent_type 即可，与本设计不冲突。
- **权衡**：选择阻塞而非真正并发——简化实现、与用户决策一致；代价是同一时刻只能跑一个子代理，不构成"并行执行"。"并行"在本设计中指"命名隔离的并行上下文"。
