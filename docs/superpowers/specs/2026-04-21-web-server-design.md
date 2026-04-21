# Web Server 模块设计文档

## 背景

coloop-agent 当前为纯 CLI 工具（`CliApp` 入口）。需要新增一个基于 Spring Boot 的 Web 服务模块，提供：
1. 聊天式 Web 界面（模仿 Claude.ai 布局）
2. WebSocket 实时推送 AgentLoop 运行日志
3. Web 端可直接发送消息触发 AgentLoop 运行

原有 `CliApp` 保留不变。

---

## 目标

- 将项目拆分为多模块 Maven 项目（`core` + `server`）
- `server` 模块作为独立 Spring Boot 应用，依赖 `core`
- `core` 模块零 Spring 依赖，保持教学骨架性质
- WebSocket 推送结构化日志，前端按类型渲染不同样式
- 所有数据完整保留（无截断），前端提供折叠/展开交互

---

## 架构

### 多模块结构

```
coloop-agent/
├── pom.xml                         ← parent POM（聚合， packaging=pom）
├── coloop-agent-core/              ← 现有代码迁移至此
│   ├── pom.xml                     ← 现有依赖（Jackson, OkHttp, JUnit）
│   └── src/main/java/com/coloop/agent/
│       ├── core/
│       ├── capability/
│       ├── runtime/
│       └── entry/
│           ├── MinimalDemo.java    ← 保留
│           └── CliApp.java         ← 保留
└── coloop-agent-server/            ← 新模块
    ├── pom.xml                     ← 依赖 core + Spring Boot
    └── src/main/java/com/coloop/agent/server/
        ├── ServerApplication.java  ← @SpringBootApplication
        ├── config/
        │   └── AgentWebConfig.java ← WebSocket 配置
        ├── controller/
        │   └── ChatController.java ← REST 端点（health check 等）
        ├── websocket/
        │   └── AgentWebSocketHandler.java
        ├── service/
        │   └── AgentService.java
        └── hook/
            └── WebSocketLoggingHook.java
    └── src/main/resources/
        ├── application.properties
        └── static/
            ├── index.html
            ├── chat.js
            └── chat.css
```

### 模块依赖

- `coloop-agent-server` 依赖 `coloop-agent-core`
- `coloop-agent-core` 不依赖任何 Spring 相关库

---

## WebSocket 消息协议

### 下行消息（服务端 → 前端）

所有消息统一格式：

```json
{
  "type": "<message_type>",
  "payload": { ... },
  "timestamp": 1713691200000
}
```

#### 消息类型定义

| type | payload 字段 | 说明 |
|------|-------------|------|
| `user` | `{ "content": "..." }` | 用户发送的消息确认 |
| `loop_start` | `{ "attempt": 1 }` | 新一轮 LLM 调用开始 |
| `thinking` | `{ "content": "...", "reasoning": "..." }` | LLM 思考/推理内容 |
| `tool_call` | `{ "name": "...", "args": "...", "fullArgs": "..." }` | 工具调用（含完整参数，无截断） |
| `tool_result` | `{ "name": "...", "result": "...", "success": true }` | 工具执行结果（完整结果，无截断） |
| `assistant` | `{ "content": "..." }` | LLM 最终回复 |
| `system` | `{ "message": "..." }` | 系统通知（如 max iterations） |
| `error` | `{ "message": "..." }` | 错误信息 |

### 上行消息（前端 → 服务端）

```json
{ "action": "chat", "message": "用户输入内容" }
```

---

## 前端设计

### 布局（模仿 Claude.ai）

```
┌────────────────────────────────────────────┐
│  coloop-agent Web                    [?]   │  ← 顶部标题栏
├────────────────────────────────────────────┤
│                                            │
│  ▶ Attempt 1...                    (居中)   │  ← loop_start
│                                            │
│  ┌─────────────────────────────┐           │
│  │ [THINKING] 推理内容...       │  [v]      │  ← thinking（可折叠）
│  └─────────────────────────────┘           │
│                                            │
│  ┌─────────────────────────────┐           │
│  │ [TOOL] ReadFileTool(...)     │  [v]      │  ← tool_call（黄色卡片）
│  └─────────────────────────────┘           │
│                                            │
│       ┌─────────────────────────────┐      │
│       │ 你好，这是 assistant 的回复... │      │  ← assistant（左侧气泡）
│       └─────────────────────────────┘      │
│                                            │
│  ┌─────────────────────────────┐           │
│  │ 帮我读取 README.md           │           │  ← user（右侧气泡）
│  └─────────────────────────────┘           │
│                                            │
├────────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐   │
│ │ 输入消息...                          │ [➤]│  ← 底部输入区
│ └──────────────────────────────────────┘   │
└────────────────────────────────────────────┘
```

### 样式规则

| 类型 | 位置 | 颜色 | 交互 |
|------|------|------|------|
| `user` | 右对齐 | 蓝色气泡 (#2563eb 背景，白色文字) | 无 |
| `assistant` | 左对齐 | 白色气泡 (#f3f4f6 背景，黑色文字) | 无 |
| `thinking` | 左对齐 | 灰色卡片 (#e5e7eb 背景) | 默认可折叠，点击展开/收起 |
| `tool_call` | 左对齐 | 黄色/橙色卡片 (#fef3c7 背景) | 默认可折叠，展开显示完整 `fullArgs` |
| `tool_result` | 左对齐 | 深色代码块 (#1f2937 背景，绿色文字) | 默认可折叠，显示前 3 行 + "..."，展开显示完整 `result` |
| `loop_start` | 居中 | 灰色小字 (#9ca3af) | 无 |
| `system` | 居中 | 橙色小字 (#f59e0b) | 无 |
| `error` | 居中 | 红色小字 (#ef4444) | 无 |

### 交互要求

1. **折叠/展开**：thinking、tool_call、tool_result 默认折叠，点击标题区域切换
2. **完整数据**：所有推送到前端的数据都是完整无截断的
3. **自动滚动**：新消息到达时，消息区域自动滚动到底部
4. **输入框**：支持 Shift+Enter 换行，Enter 发送
5. **连接状态**：WebSocket 连接断开时，输入框禁用并显示重连提示

---

## 核心组件

### AgentService

管理 AgentLoop 的生命周期：

- `startChat(String userMessage, WebSocketSession session)`：创建 AgentLoop 实例（带 WebSocketLoggingHook），在后台线程启动 `chatStream()`
- 每个 WebSocket 连接对应一个独立的 AgentLoop 实例（单用户场景）
- 使用 `ExecutorService` 管理后台线程

### AgentWebSocketHandler

Spring `TextWebSocketHandler` 实现：

- `afterConnectionEstablished`：保存 WebSocket Session
- `handleTextMessage`：解析前端 JSON，调用 `AgentService.startChat()`
- `afterConnectionClosed`：清理资源

### WebSocketLoggingHook

实现 `AgentHook` 接口：

- 持有 `WebSocketSession`
- 每个生命周期回调将数据封装为结构化 JSON，通过 `session.sendMessage()` 推送
- 推送失败时静默处理（打印日志，不中断 AgentLoop）

---

## 数据流

```
用户输入 → 前端 WebSocket 发送 {action:"chat", message:"..."}
    ↓
AgentWebSocketHandler.handleTextMessage()
    ↓
AgentService.startChat() 在后台线程执行
    ↓
CapabilityLoader 组装 AgentLoop（含 WebSocketLoggingHook）
    ↓
AgentLoop.chatStream() 运行
    ↓
WebSocketLoggingHook 在各生命周期回调中 → session.sendMessage(JSON)
    ↓
前端接收 JSON → 根据 type 渲染不同 UI 组件
```

---

## 错误处理

| 场景 | 处理方式 |
|------|---------|
| WebSocket 断开 | 前端显示"连接已断开"，禁用输入，提供重连按钮 |
| AgentLoop 异常 | WebSocketLoggingHook 捕获异常，推送 `type:error` 消息 |
| LLM API 失败 | provider 返回错误，Hook 推送 error，Loop 结束 |
| 前端发送非法 JSON | 服务端回复 `type:error` 提示格式错误 |

---

## 不实现（YAGNI）

- 多用户/会话隔离（当前单实例，单 WebSocket 连接）
- 消息历史持久化（刷新页面即清空）
- 前端路由/多页面
- 用户认证/授权
- 文件上传
- 流式打字机效果（直接显示完整内容即可）
