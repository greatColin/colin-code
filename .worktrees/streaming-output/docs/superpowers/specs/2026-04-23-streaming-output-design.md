# 前端流式输出改造设计

## 日期
2026-04-23

## 背景

当前 `coloop-agent-server` 的 Web 前端通过 WebSocket 接收 LLM 响应，但采用的是**同步模式**：
- `AgentService.startChat()` 调用 `agentLoop.chat()`
- `WebSocketLoggingHook.onLoopEnd()` 在 LLM 完全响应后一次性推送 `assistant` 消息
- 前端 `renderAssistant()` 一次性渲染完整 Markdown

用户无法看到生成过程，体验不佳。

## 目标

让前端能够**实时看到 LLM 的生成过程**，同时保持架构的简洁性和向后兼容性。

## 设计

### 架构总览

```
┌─────────────┐     WebSocket      ┌─────────────────┐
│   chat.js   │ ◄───────────────── │ AgentService    │
│  (frontend) │  stream_chunk      │  (backend)      │
│             │ ◄───────────────── │                 │
│             │  assistant (end)   │                 │
└─────────────┘                    └────────┬────────┘
                                            │
                                   ┌────────▼────────┐
                                   │  AgentLoop      │
                                   │  chatStream()   │
                                   └────────┬────────┘
                                            │
                                   ┌────────▼────────┐
                                   │  OpenAICompat   │
                                   │  chatStream()   │
                                   └─────────────────┘
```

### 后端改动

#### 1. AgentHook 新增流式回调

```java
default void onStreamChunk(String chunk) {}
```

在 LLM 流式生成过程中，每收到一个内容块即调用。

#### 2. AgentLoop.chatStream 触发 Hook

在 `StreamConsumer.onContent()` 中，除向外部 consumer 回调外，同时触发所有 Hook 的 `onStreamChunk()`。

#### 3. WebSocketLoggingHook 实现 onStreamChunk

发送 `type: "stream_chunk"` 的 WebSocket 消息，payload 包含 `content: chunk`。

#### 4. WebSocketMessage 新增工厂方法

```java
public static WebSocketMessage streamChunk(String chunk) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("content", chunk);
    return new WebSocketMessage("stream_chunk", payload);
}
```

#### 5. AgentService 切换到 chatStream 模式

`startChat()` 中调用 `agentLoop.chatStream(userMessage, consumer)` 替代 `agentLoop.chat()`。`StreamConsumer` 的实现主要用于调试/日志，WebSocket 推送仍由 Hook 负责。

> **注意**：保持 `onLoopEnd()` 发送完整 `assistant` 消息，作为流结束标记。这确保了未改造的前端仍能正常工作。

### 前端改动

#### 1. handleMessage 新增 stream_chunk 分支

```javascript
case 'stream_chunk':
    appendStreamChunk(msg.payload.content);
    break;
```

#### 2. 新增流式渲染状态管理

```javascript
let currentAssistantEl = null;
let streamBuffer = '';
let renderDebounceTimer = null;
```

#### 3. appendStreamChunk 实现

- 若 `currentAssistantEl` 为空，创建新的 assistant 消息 div
- 将 chunk 追加到 `streamBuffer`
- 使用防抖（debounce）策略：每 100ms 或累积 50 个字符后重新渲染 Markdown
- Markdown 渲染后应用代码高亮

#### 4. assistant 消息作为流结束标记

当收到 `type: 'assistant'` 时：
- 清空 `currentAssistantEl` 和 `streamBuffer`
- 用完整内容重新渲染（确保最终显示正确）
- 应用代码高亮

### 防抖渲染策略（方案 C）

为了平衡流畅度和性能：
- **触发条件**：
  - 收到 chunk 时，若距离上次渲染超过 100ms，立即渲染
  - 或累积字符超过 50 个，立即渲染
  - 或收到流结束标记（assistant 消息），立即渲染
- **Markdown 渲染**：使用完整的 `streamBuffer` 重新渲染，而非增量追加
- **代码高亮**：仅在流结束时或停顿超过 300ms 时执行（避免频繁 DOM 操作）

### 向后兼容性

- 未改造的前端：会忽略 `stream_chunk` 消息，仍能在收到 `assistant` 时正常显示
- 新前端 + 旧后端：如果后端不发 `stream_chunk`，行为与当前相同（等待 `assistant`）

### 错误处理

- WebSocket 在流式输出期间断开：前端保留已渲染内容，显示断连提示
- 后端流式解析错误：通过现有的 `error` 类型消息通知前端

## 文件变更清单

| 文件 | 改动类型 |
|------|----------|
| `core/agent/AgentHook.java` | 新增 `onStreamChunk` 默认方法 |
| `core/agent/AgentLoop.java` | `chatStream()` 中触发 `onStreamChunk` |
| `server/hook/WebSocketLoggingHook.java` | 实现 `onStreamChunk`，发送 stream_chunk 消息 |
| `server/dto/WebSocketMessage.java` | 新增 `streamChunk()` 工厂方法 |
| `server/service/AgentService.java` | 切换到 `chatStream()` 模式 |
| `server/resources/static/chat.js` | 新增流式渲染逻辑 |

## 测试计划

1. 启动服务器，连接前端
2. 发送消息，观察是否逐字/逐块显示
3. 验证 Markdown 渲染正确（标题、列表、代码块等）
4. 验证代码高亮在流结束后正确应用
5. 验证工具调用（thinking/tool_call/tool_result）仍正常显示
6. 测试多轮对话，验证上下文保持
