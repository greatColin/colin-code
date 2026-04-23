# 前端流式输出改造实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通后端 LLM 流式生成到前端增量渲染的完整链路，让用户实时看到生成过程。

**Architecture:** 后端 `AgentHook` 新增 `onStreamChunk` 回调，`AgentLoop.chatStream()` 在 `StreamConsumer.onContent()` 中触发它；`WebSocketLoggingHook` 将 chunk 转为 WebSocket `stream_chunk` 消息；前端 `chat.js` 增量追加内容并防抖渲染 Markdown。

**Tech Stack:** Java 21, Spring WebSocket, Vanilla JS, Maven

---

## 文件变更映射

| 文件 | 责任 |
|------|------|
| `AgentHook.java` | 新增流式内容回调接口 `onStreamChunk` |
| `AgentLoop.java` | `chatStream()` 中向 Hook 转发流式 chunk |
| `WebSocketMessage.java` | 新增 `streamChunk()` 工厂方法 |
| `WebSocketLoggingHook.java` | 实现 `onStreamChunk`，推送 WebSocket 消息 |
| `AgentService.java` | 从 `chat()` 切换到 `chatStream()` |
| `chat.js` | 新增 `stream_chunk` 消息处理与增量渲染逻辑 |

---

### Task 1: AgentHook 新增 onStreamChunk 回调

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/core/agent/AgentHook.java`

- [ ] **Step 1: 在 AgentHook 接口中添加 onStreamChunk 默认方法**

在 `onUserMessageInjected` 方法之后添加：

```java
    /** 在 LLM 流式生成过程中，每收到一个内容块时调用。 */
    default void onStreamChunk(String chunk) {}
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/core/agent/AgentHook.java
git commit -m "feat(core): add onStreamChunk hook for streaming output"
```

---

### Task 2: AgentLoop.chatStream 触发 onStreamChunk

**Files:**
- Modify: `coloop-agent-core/src/main/java/com/coloop/agent/core/agent/AgentLoop.java`

- [ ] **Step 1: 在 chatStream 的 StreamConsumer.onContent 中触发 Hook**

找到 `chatStream` 方法中 `accumulatingConsumer` 的 `onContent` 实现（约第 177-180 行），修改为：

```java
                @Override
                public void onContent(String chunk) {
                    if (consumer != null) {
                        consumer.onContent(chunk);
                    }
                    for (AgentHook h : hooks) {
                        h.onStreamChunk(chunk);
                    }
                }
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/core/agent/AgentLoop.java
git commit -m "feat(core): trigger onStreamChunk hook in chatStream"
```

---

### Task 3: WebSocketMessage 新增 streamChunk 工厂方法

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java`

- [ ] **Step 1: 在 assistant 工厂方法之前添加 streamChunk 方法**

在 `assistant` 方法之前添加：

```java
    public static WebSocketMessage streamChunk(String chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", chunk);
        return new WebSocketMessage("stream_chunk", payload);
    }
```

- [ ] **Step 2: 编译验证**

```bash
cd coloop-agent-server && mvn compile -q
```

Expected: 编译成功，无错误。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java
git commit -m "feat(server): add streamChunk WebSocket message type"
```

---

### Task 4: WebSocketLoggingHook 实现 onStreamChunk

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/hook/WebSocketLoggingHook.java`

- [ ] **Step 1: 覆写 onStreamChunk 方法**

在 `onUserMessageInjected` 方法之后添加：

```java
    @Override
    public void onStreamChunk(String chunk) {
        send(WebSocketMessage.streamChunk(chunk));
    }
```

- [ ] **Step 2: 编译验证**

```bash
cd coloop-agent-server && mvn compile -q
```

Expected: 编译成功，无错误。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/hook/WebSocketLoggingHook.java
git commit -m "feat(server): push stream chunks via WebSocket"
```

---

### Task 5: AgentService 切换到 chatStream 模式

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: 将 agentLoop.chat() 替换为 agentLoop.chatStream()**

找到 `startChat` 方法中调用 `agentLoop.chat(userMessage)` 的行（约第 115 行），替换为：

```java
                agentLoop.chatStream(userMessage, new LLMProvider.StreamConsumer() {
                    @Override
                    public void onContent(String chunk) {
                        // chunk 已通过 Hook 推送到前端，此处无需额外操作
                    }

                    @Override
                    public void onToolCall(ToolCallRequest toolCall) {
                        // tool call 已通过 Hook 推送到前端
                    }

                    @Override
                    public void onComplete(LLMResponse response) {
                        // 完成，Hook 的 onLoopEnd 已发送 assistant 消息
                    }

                    @Override
                    public void onError(String error) {
                        sendError(session, error);
                    }
                });
```

同时需要在文件顶部添加 import：

```java
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.core.provider.LLMResponse;
```

- [ ] **Step 2: 编译验证**

```bash
cd coloop-agent-server && mvn compile -q
```

Expected: 编译成功，无错误。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java
git commit -m "feat(server): switch to chatStream for WebSocket sessions"
```

---

### Task 6: 前端 chat.js 流式渲染

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: 在 IIFE 顶部添加流式状态变量**

在 `const wsUrl = ...` 之前添加：

```javascript
    // --- Streaming state ---
    let currentAssistantEl = null;
    let streamBuffer = '';
    let lastRenderTime = 0;
    const STREAM_RENDER_INTERVAL = 100;   // ms
    const STREAM_RENDER_MIN_CHARS = 50;   // chars
    let streamRenderTimer = null;
```

- [ ] **Step 2: 在 handleMessage 中添加 stream_chunk 分支**

在 `case 'commands':` 之前添加：

```javascript
            case 'stream_chunk':
                appendStreamChunk(msg.payload.content);
                break;
```

同时修改 `case 'assistant'` 分支：

```javascript
            case 'assistant':
                finalizeAssistant(msg.payload.content);
                break;
```

- [ ] **Step 3: 添加 appendStreamChunk 和 finalizeAssistant 函数**

在 `renderAssistant` 函数之前添加：

```javascript
    function appendStreamChunk(chunk) {
        if (!chunk) return;

        if (!currentAssistantEl) {
            currentAssistantEl = document.createElement('div');
            currentAssistantEl.className = 'message assistant';
            currentAssistantEl.innerHTML = '<span class="stream-cursor"></span>';
            appendElement(currentAssistantEl);
        }

        streamBuffer += chunk;

        var now = Date.now();
        if (now - lastRenderTime > STREAM_RENDER_INTERVAL || streamBuffer.length > STREAM_RENDER_MIN_CHARS) {
            renderStreamBuffer();
        } else if (!streamRenderTimer) {
            streamRenderTimer = setTimeout(function() {
                renderStreamBuffer();
            }, STREAM_RENDER_INTERVAL);
        }

        scrollToBottom();
    }

    function renderStreamBuffer() {
        if (!currentAssistantEl) return;

        lastRenderTime = Date.now();
        streamRenderTimer = null;

        var html = renderMarkdown(streamBuffer);
        currentAssistantEl.innerHTML = html + '<span class="stream-cursor"></span>';
        highlightCodeBlocks(currentAssistantEl);
    }

    function finalizeAssistant(fullContent) {
        // Clear any pending render timer
        if (streamRenderTimer) {
            clearTimeout(streamRenderTimer);
            streamRenderTimer = null;
        }

        // Extract think blocks from the full content
        var extracted = extractThinkBlocks(fullContent || '');

        // Render think content as a thinking card (if any)
        if (extracted.thinkContent) {
            renderCard('thinking', '💭 Thinking', extracted.thinkContent);
        }

        if (currentAssistantEl) {
            // Update with final rendered content (no cursor)
            var html = renderMarkdown(extracted.remainingContent);
            currentAssistantEl.innerHTML = html;
            highlightCodeBlocks(currentAssistantEl);
            currentAssistantEl = null;
            streamBuffer = '';
        } else {
            // Fallback: no stream chunks received, render as before
            var el = document.createElement('div');
            el.className = 'message assistant';
            el.innerHTML = renderMarkdown(extracted.remainingContent);
            appendElement(el);
            highlightCodeBlocks(el);
        }
    }
```

- [ ] **Step 4: 删除旧的 renderAssistant 函数**

将原来的 `renderAssistant` 函数（约第 166-183 行）删除，因为已被 `finalizeAssistant` 替代。

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(ui): incremental streaming render for assistant messages"
```

---

### Task 7: 添加流式光标 CSS

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/themes/default.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/claude.css`

- [ ] **Step 1: 在 default.css 中添加 stream-cursor 样式**

在文件末尾添加：

```css
.stream-cursor {
    display: inline-block;
    width: 8px;
    height: 1.2em;
    background: currentColor;
    margin-left: 2px;
    vertical-align: text-bottom;
    animation: stream-blink 1s step-end infinite;
    opacity: 0.7;
}

@keyframes stream-blink {
    0%, 100% { opacity: 0.7; }
    50% { opacity: 0; }
}
```

- [ ] **Step 2: 在 claude.css 中添加相同的 stream-cursor 样式**

同上，在文件末尾添加。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/themes/default.css coloop-agent-server/src/main/resources/static/themes/claude.css
git commit -m "style(ui): add streaming cursor animation"
```

---

### Task 8: 集成编译测试

**Files:** N/A

- [ ] **Step 1: 全项目编译**

```bash
mvn compile -q
```

Expected: 编译成功，无错误。

- [ ] **Step 2: 运行现有单元测试**

```bash
mvn test -q
```

Expected: 所有现有测试通过。

- [ ] **Step 3: Commit（如有测试文件更新）**

---

### Task 9: 手动端到端测试

**Files:** N/A

- [ ] **Step 1: 启动服务器**

```bash
cd coloop-agent-server
mvn spring-boot:run
```

- [ ] **Step 2: 打开浏览器访问 `http://localhost:8080`**

- [ ] **Step 3: 发送测试消息**

观察：
- assistant 消息是否逐块出现
- Markdown 渲染是否正确（标题、列表、代码块）
- 流结束后光标是否消失
- 代码高亮是否正常

- [ ] **Step 4: 测试工具调用**

发送要求使用工具的消息（如"列出当前目录文件"），验证：
- thinking 卡片仍正常显示
- tool_call / tool_result 卡片正常
- 最终 assistant 回复正确

- [ ] **Step 5: 测试多轮对话**

连续发送多条消息，验证上下文保持。

---

## Spec 覆盖自查

| 设计需求 | 对应任务 |
|----------|----------|
| AgentHook 新增 `onStreamChunk` | Task 1 |
| AgentLoop.chatStream 触发 Hook | Task 2 |
| WebSocketMessage.streamChunk 工厂方法 | Task 3 |
| WebSocketLoggingHook 推送 stream_chunk | Task 4 |
| AgentService 切换到 chatStream | Task 5 |
| 前端 stream_chunk 消息处理 | Task 6 |
| 防抖渲染策略（100ms / 50 chars） | Task 6 |
| 流结束标记（assistant 消息） | Task 6 finalizeAssistant |
| 向后兼容 | Task 6（保留 assistant 完整消息） |
| 代码高亮 | Task 6（highlightCodeBlocks） |
| 视觉反馈（光标） | Task 7 |

## 无占位符检查

- [x] 所有代码步骤包含完整代码
- [x] 所有命令包含预期输出
- [x] 无 TBD/TODO/实现稍后
- [x] 类型一致（onStreamChunk, stream_chunk, streamBuffer 等）
