# 长期记忆模块设计文档

> **目标：** 为 coloop-agent 增加会话历史持久化能力，支持自动保存、历史列表浏览、历史会话加载，并将前端侧边栏重构为一级/二级菜单形式。

**架构：** 基于 `AgentHook` 生命周期钩子实现无侵入式消息捕获，文件系统 JSON 存储（零额外依赖），前端 accordion 菜单支持当前会话与历史记录两级结构。

**Tech Stack:** Java 17, Jackson, Spring Boot WebSocket, Vanilla JS

---

## 1. 存储结构

项目根目录创建 `.history/` 文件夹，已加入 `.gitignore`。

```
.history/
  index.json                 -- 会话索引，按时间倒序
  <sessionId>/               -- UUID 或时间戳格式
    meta.json                -- 会话元数据
    messages.json            -- 消息数组（按时间顺序）
```

### 1.1 index.json
```json
[
  {
    "id": "20260508-143022-a1b2",
    "title": "如何配置 Spring Boot...",
    "createdAt": 1715136000000,
    "updatedAt": 1715139600000
  }
]
```

### 1.2 meta.json
```json
{
  "id": "20260508-143022-a1b2",
  "title": "如何配置 Spring Boot...",
  "createdAt": 1715136000000,
  "updatedAt": 1715139600000
}
```

### 1.3 messages.json
每条消息包含：`type`, `agent`, `timestamp`, 以及类型特定字段。

```json
[
  {"type": "user", "agent": "main", "content": "如何配置 Spring Boot 数据源", "timestamp": 1715136000000},
  {"type": "loop_start", "agent": "main", "attempt": 1, "timestamp": 1715136001000},
  {"type": "thinking", "agent": "main", "content": "用户想了解...", "timestamp": 1715136002000},
  {"type": "tool_call", "agent": "main", "name": "ReadFile", "args": "pom.xml", "fullArgs": "{\"path\":\"pom.xml\"}", "timestamp": 1715136003000},
  {"type": "tool_result", "agent": "main", "name": "ReadFile", "result": "...", "success": true, "timestamp": 1715136004000},
  {"type": "assistant", "agent": "main", "content": "你可以在 pom.xml 中添加...", "timestamp": 1715136005000},
  {"type": "subagent_created", "agent": "coder", "description": "代码助手", "timestamp": 1715136100000},
  {"type": "user", "agent": "coder", "content": "帮我写个示例", "timestamp": 1715136101000},
  {"type": "assistant", "agent": "coder", "content": "好的，示例代码如下...", "timestamp": 1715136105000}
]
```

---

## 2. 后端组件

### 2.1 ConversationHistoryStore（接口）

```java
public interface ConversationHistoryStore {
    String createSession();
    void saveMessage(String sessionId, HistoryMessage message);
    SessionMeta loadSessionMeta(String sessionId);
    List<HistoryMessage> loadMessages(String sessionId);
    List<SessionMeta> listSessions();
    void updateTitle(String sessionId, String title);
}
```

### 2.2 FileSystemHistoryStore（实现）

- 基于 Jackson `ObjectMapper` 读写 JSON
- 目录：`baseDir/.history/`
- `saveMessage` 采用追加写模式：读取现有数组 → 添加新元素 → 写回
- `listSessions` 读取 `index.json`，按 `updatedAt` 倒序
- 所有公共方法 `synchronized`（同一 JVM 内串行化，足够当前场景）

### 2.3 HistoryRecordingHook

实现 `AgentHook`，注入到主 AgentLoop 和每个 subagent 的 AgentLoop 中。

| Hook 方法 | 保存的消息类型 |
|-----------|---------------|
| `onLoopStart` | `user` |
| `beforeLLMCall` | `loop_start` |
| `onThinking` | `thinking` |
| `onToolCall` | `tool_call` + `tool_result` |
| `onStreamChunk` | **不保存**（避免数据膨胀，最终 assistant 消息已包含完整内容）|
| `onLoopEnd` | `assistant` 或 `system`；更新 `index.json` 的 `updatedAt` |
| `onUserMessageInjected` | `user` |

注意：subagent 的 `agentName` 通过 hook 构造时传入。主 agent 的 `agentName` 为 `"main"`。

### 2.4 会话标题生成

- 第一次保存 `user` 类型消息时，取 `content` 前 30 个字符
- 如果超过 30 字符，截断并追加 `"..."`
- 如果内容为空，使用 `"New Session"`
- 标题只在会话创建时生成一次，后续不再自动更新（避免漂移）

### 2.5 AgentService 扩展

WebSocket `handleTextMessage` 新增 action：

- `list_history` → 调用 `store.listSessions()` → 发送 `history_list`
- `load_session` → 调用 `store.loadMessages(sessionId)` → 逐条发送消息到前端（按原始顺序）→ 最后发送 `session_loaded`

**加载历史时的行为：**
- 后端不重建 AgentLoop，仅作为"回放"推送消息
- 前端收到消息后按正常流程渲染，但不做 LLM 调用
- `session_loaded` 通知前端加载完成，前端将当前活跃会话切换为加载的会话

### 2.6 会话生命周期

| 事件 | 行为 |
|------|------|
| WebSocket 连接建立 | 创建新 sessionId，开始记录到 `.history/<id>/` |
| `/new` 命令执行 | `agentLoop.reset()`，创建新 sessionId，开始新记录 |
| WebSocket 断开 | 当前会话记录停止，无额外清理 |
| 子代理创建 | 子代理的 `HistoryRecordingHook` 自动继承同一个 `sessionId` |
| 子代理清空 | 继续记录到同一个会话（子代理重建视为同一会话内的变化）|

---

## 3. 前端改造

### 3.1 侧边栏结构

```html
<aside class="agent-sidebar">
  <!-- 一级菜单：当前会话 -->
  <div class="sidebar-section">
    <div class="sidebar-section-header" data-section="current">
      <span class="section-toggle">▼</span>
      <span class="section-title">当前会话</span>
    </div>
    <div class="sidebar-section-body" id="current-session-list">
      <div class="agent-item active" data-agent="main">...</div>
      <!-- subagents 动态插入 -->
    </div>
  </div>

  <!-- 一级菜单：历史记录 -->
  <div class="sidebar-section">
    <div class="sidebar-section-header" data-section="history">
      <span class="section-toggle">▶</span>
      <span class="section-title">历史记录</span>
    </div>
    <div class="sidebar-section-body collapsed" id="history-list">
      <!-- 历史会话动态插入 -->
    </div>
  </div>
</aside>
```

### 3.2 交互行为

- **当前会话**：默认展开，点击 subagent 项切换聊天视图（现有行为保留）
- **历史记录**：默认收起，点击 header 展开/收起
- **历史会话项**：点击后发送 `action: load_session` + `sessionId`
- **加载完成后**：
  - 清空 `agentState` 和 `chatContainer`
  - 重建 `agentState`（至少包含 `main`）
  - 按消息顺序逐条渲染
  - 侧边栏"当前会话"更新为加载的会话中的 agents
  - 自动切换到 `main` agent 视图

### 3.3 CSS 变量（主题适配）

新增 sidebar section 相关 CSS 变量，放入各 theme CSS 文件：

```css
--sidebar-section-header-bg
--sidebar-section-header-color
--sidebar-history-item-color
--sidebar-history-item-hover-bg
```

---

## 4. WebSocket 消息新增

### 4.1 后端 → 前端

```java
// 历史列表
WebSocketMessage.historyList(List<SessionMeta> sessions)
// type: "history_list", payload: {sessions: [{id, title, createdAt, updatedAt}]}

// 会话加载完成
WebSocketMessage.sessionLoaded(String sessionId, String title)
// type: "session_loaded", payload: {sessionId, title}
```

### 4.2 前端 → 后端

```json
{"action": "list_history"}
{"action": "load_session", "sessionId": "20260508-143022-a1b2"}
```

---

## 5. 文件变更清单

### 新增文件
- `coloop-agent-core/src/main/java/com/coloop/agent/core/history/ConversationHistoryStore.java`
- `coloop-agent-core/src/main/java/com/coloop/agent/core/history/FileSystemHistoryStore.java`
- `coloop-agent-core/src/main/java/com/coloop/agent/core/history/HistoryMessage.java`
- `coloop-agent-core/src/main/java/com/coloop/agent/core/history/SessionMeta.java`
- `coloop-agent-core/src/main/java/com/coloop/agent/core/history/HistoryRecordingHook.java`

### 修改文件
- `.gitignore` — 添加 `.history/`
- `coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java` — 新增 action 处理
- `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java` — 集成 HistoryStore、创建 session、处理 load_session
- `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java` — 新增 factory 方法
- `coloop-agent-server/src/main/resources/static/index.html` — 侧边栏重构
- `coloop-agent-server/src/main/resources/static/chat.js` — 历史列表加载、会话加载、sidebar 交互
- `coloop-agent-server/src/main/resources/static/themes/*.css` — 新增 sidebar section 样式变量

---

## 6. 自检

- [x] **Spec coverage:** 所有需求点（文件夹、gitignore、侧边栏菜单、会话标题、存储结构、加载机制）均有对应任务
- [x] **Placeholder scan:** 无 TBD/TODO/"implement later"
- [x] **Internal consistency:** 存储路径、消息类型、WebSocket 消息类型前后一致
- [x] **Scope check:** 单会话内聚焦，不引入搜索/过滤等超出范围的功能
- [x] **Ambiguity check:** 标题生成规则明确（30 字截断），加载行为明确（仅回放不重建 AgentLoop）
