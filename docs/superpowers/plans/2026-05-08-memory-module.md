# 长期记忆模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现会话历史自动持久化、历史列表浏览、历史会话加载，并将前端侧边栏重构为一级/二级菜单。

**Architecture:** 基于 `AgentHook` 无侵入捕获消息，文件系统 JSON 存储，前端 accordion 菜单。主 AgentLoop 和各 subagent 的 AgentLoop 均注入同一个 `HistoryRecordingHook` 实例，共享同一个 `sessionId`。

**Tech Stack:** Java 17, Jackson, Spring Boot WebSocket, Vanilla JS

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `.gitignore` | 添加 `.history/` 忽略 |
| `core/history/HistoryMessage.java` | 历史消息数据模型 |
| `core/history/SessionMeta.java` | 会话元数据模型 |
| `core/history/ConversationHistoryStore.java` | 存储接口 |
| `core/history/FileSystemHistoryStore.java` | 文件系统存储实现 |
| `core/history/HistoryRecordingHook.java` | AgentHook 实现，捕获生命周期事件 |
| `server/dto/WebSocketMessage.java` | 新增 `historyList`, `sessionLoaded` factory 方法 |
| `server/websocket/AgentWebSocketHandler.java` | 新增 `list_history`, `load_session` action 处理 |
| `server/service/AgentService.java` | 集成 HistoryStore、创建 session、hook 注入 |
| `server/resources/static/index.html` | 侧边栏重构为 accordion |
| `server/resources/static/chat.js` | 历史列表、会话加载、sidebar 交互 |
| `server/resources/static/themes/*.css` | 新增 sidebar section CSS 变量 |

---

### Task 1: 更新 .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: 添加 `.history/` 到 gitignore**

在 `.gitignore` 末尾追加：

```
# Conversation history
.history/
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore .history/ directory"
```

---

### Task 2: 数据模型（HistoryMessage + SessionMeta）

**Files:**
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/core/history/HistoryMessage.java`
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/core/history/SessionMeta.java`

- [ ] **Step 1: 创建 HistoryMessage.java**

```java
package com.coloop.agent.core.history;

import java.util.Map;

/**
 * 历史消息记录。对应前端渲染的每一条消息。
 */
public class HistoryMessage {
    public String type;      // user, assistant, thinking, tool_call, tool_result, loop_start, system, subagent_created
    public String agent;     // "main" 或 subagent name
    public long timestamp;

    // type-specific fields
    public String content;
    public String name;      // tool_call/tool_result 的 tool name
    public String args;      // tool_call 的格式化参数
    public String fullArgs;  // tool_call 的完整 JSON 参数
    public String result;    // tool_result 的结果
    public Boolean success;  // tool_result 是否成功
    public Integer attempt;  // loop_start 的 attempt 数
    public String description; // subagent_created 的描述
    public String message;   // system 的消息内容
    public String reasoning; // thinking 的 reasoningContent

    public HistoryMessage() {}

    public static HistoryMessage user(String agent, String content) {
        HistoryMessage m = new HistoryMessage();
        m.type = "user";
        m.agent = agent;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage assistant(String agent, String content) {
        HistoryMessage m = new HistoryMessage();
        m.type = "assistant";
        m.agent = agent;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage system(String agent, String message) {
        HistoryMessage m = new HistoryMessage();
        m.type = "system";
        m.agent = agent;
        m.message = message;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage thinking(String agent, String content, String reasoning) {
        HistoryMessage m = new HistoryMessage();
        m.type = "thinking";
        m.agent = agent;
        m.content = content;
        m.reasoning = reasoning;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage toolCall(String agent, String name, String args, String fullArgs) {
        HistoryMessage m = new HistoryMessage();
        m.type = "tool_call";
        m.agent = agent;
        m.name = name;
        m.args = args;
        m.fullArgs = fullArgs;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage toolResult(String agent, String name, String result, boolean success) {
        HistoryMessage m = new HistoryMessage();
        m.type = "tool_result";
        m.agent = agent;
        m.name = name;
        m.result = result;
        m.success = success;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage loopStart(String agent, int attempt) {
        HistoryMessage m = new HistoryMessage();
        m.type = "loop_start";
        m.agent = agent;
        m.attempt = attempt;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static HistoryMessage subagentCreated(String agent, String description) {
        HistoryMessage m = new HistoryMessage();
        m.type = "subagent_created";
        m.agent = agent;
        m.description = description;
        m.timestamp = System.currentTimeMillis();
        return m;
    }
}
```

- [ ] **Step 2: 创建 SessionMeta.java**

```java
package com.coloop.agent.core.history;

/**
 * 会话元数据。
 */
public class SessionMeta {
    public String id;
    public String title;
    public long createdAt;
    public long updatedAt;

    public SessionMeta() {}

    public SessionMeta(String id, String title, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl coloop-agent-core -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/core/history/
git commit -m "feat(history): add HistoryMessage and SessionMeta data models"
```

---

### Task 3: 存储接口与实现

**Files:**
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/core/history/ConversationHistoryStore.java`
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/core/history/FileSystemHistoryStore.java`

- [ ] **Step 1: 创建 ConversationHistoryStore.java**

```java
package com.coloop.agent.core.history;

import java.util.List;

public interface ConversationHistoryStore {
    String createSession();
    void saveMessage(String sessionId, HistoryMessage message);
    SessionMeta loadSessionMeta(String sessionId);
    List<HistoryMessage> loadMessages(String sessionId);
    List<SessionMeta> listSessions();
    void updateTitle(String sessionId, String title);
}
```

- [ ] **Step 2: 创建 FileSystemHistoryStore.java**

```java
package com.coloop.agent.core.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileSystemHistoryStore implements ConversationHistoryStore {

    private final Path baseDir;
    private final ObjectMapper mapper;

    public FileSystemHistoryStore(Path baseDir) {
        this.baseDir = baseDir.resolve(".history");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            System.err.println("Failed to create history directory: " + e.getMessage());
        }
    }

    @Override
    public synchronized String createSession() {
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-"))
                + randomSuffix();
        Path sessionDir = baseDir.resolve(id);
        try {
            Files.createDirectories(sessionDir);
            SessionMeta meta = new SessionMeta(id, "New Session", System.currentTimeMillis(), System.currentTimeMillis());
            writeJson(sessionDir.resolve("meta.json"), meta);
            writeJson(sessionDir.resolve("messages.json"), new ArrayList<HistoryMessage>());
            updateIndex(meta);
        } catch (IOException e) {
            System.err.println("Failed to create session: " + e.getMessage());
        }
        return id;
    }

    @Override
    public synchronized void saveMessage(String sessionId, HistoryMessage message) {
        Path sessionDir = baseDir.resolve(sessionId);
        Path messagesFile = sessionDir.resolve("messages.json");
        try {
            List<HistoryMessage> messages;
            if (Files.exists(messagesFile)) {
                messages = mapper.readValue(messagesFile.toFile(), new TypeReference<List<HistoryMessage>>() {});
            } else {
                messages = new ArrayList<>();
            }
            messages.add(message);
            writeJson(messagesFile, messages);

            // Update meta updatedAt
            SessionMeta meta = loadSessionMeta(sessionId);
            if (meta != null) {
                meta.updatedAt = System.currentTimeMillis();
                writeJson(sessionDir.resolve("meta.json"), meta);
                updateIndex(meta);
            }
        } catch (IOException e) {
            System.err.println("Failed to save message: " + e.getMessage());
        }
    }

    @Override
    public synchronized SessionMeta loadSessionMeta(String sessionId) {
        Path file = baseDir.resolve(sessionId).resolve("meta.json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), SessionMeta.class);
        } catch (IOException e) {
            System.err.println("Failed to load session meta: " + e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized List<HistoryMessage> loadMessages(String sessionId) {
        Path file = baseDir.resolve(sessionId).resolve("messages.json");
        if (!Files.exists(file)) return Collections.emptyList();
        try {
            return mapper.readValue(file.toFile(), new TypeReference<List<HistoryMessage>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load messages: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized List<SessionMeta> listSessions() {
        Path indexFile = baseDir.resolve("index.json");
        if (!Files.exists(indexFile)) return Collections.emptyList();
        try {
            List<SessionMeta> list = mapper.readValue(indexFile.toFile(), new TypeReference<List<SessionMeta>>() {});
            list.sort(Comparator.comparingLong((SessionMeta m) -> m.updatedAt).reversed());
            return list;
        } catch (IOException e) {
            System.err.println("Failed to list sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized void updateTitle(String sessionId, String title) {
        Path sessionDir = baseDir.resolve(sessionId);
        Path metaFile = sessionDir.resolve("meta.json");
        try {
            SessionMeta meta;
            if (Files.exists(metaFile)) {
                meta = mapper.readValue(metaFile.toFile(), SessionMeta.class);
            } else {
                meta = new SessionMeta();
                meta.id = sessionId;
                meta.createdAt = System.currentTimeMillis();
            }
            meta.title = title;
            meta.updatedAt = System.currentTimeMillis();
            writeJson(metaFile, meta);
            updateIndex(meta);
        } catch (IOException e) {
            System.err.println("Failed to update title: " + e.getMessage());
        }
    }

    private void updateIndex(SessionMeta meta) throws IOException {
        Path indexFile = baseDir.resolve("index.json");
        List<SessionMeta> list = new ArrayList<>();
        if (Files.exists(indexFile)) {
            list = mapper.readValue(indexFile.toFile(), new TypeReference<List<SessionMeta>>() {});
        }
        list.removeIf(m -> m.id.equals(meta.id));
        list.add(meta);
        list.sort(Comparator.comparingLong((SessionMeta m) -> m.updatedAt).reversed());
        writeJson(indexFile, list);
    }

    private void writeJson(Path path, Object value) throws IOException {
        mapper.writeValue(path.toFile(), value);
    }

    private String randomSuffix() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl coloop-agent-core -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/core/history/
git commit -m "feat(history): add ConversationHistoryStore and FileSystemHistoryStore"
```

---

### Task 4: HistoryRecordingHook

**Files:**
- Create: `coloop-agent-core/src/main/java/com/coloop/agent/core/history/HistoryRecordingHook.java`

- [ ] **Step 1: 创建 HistoryRecordingHook.java**

```java
package com.coloop.agent.core.history;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;

/**
 * 历史记录 Hook。捕获 AgentLoop 生命周期事件并保存到 ConversationHistoryStore。
 * 主 Agent 和各 subagent 共享同一个 store 实例，但各自传入自己的 agentName。
 */
public class HistoryRecordingHook implements AgentHook {

    private final ConversationHistoryStore store;
    private final String sessionId;
    private final String agentName;
    private boolean titleGenerated = false;

    public HistoryRecordingHook(ConversationHistoryStore store, String sessionId, String agentName) {
        this.store = store;
        this.sessionId = sessionId;
        this.agentName = agentName;
    }

    @Override
    public void onLoopStart(String userMessage) {
        store.saveMessage(sessionId, HistoryMessage.user(agentName, userMessage));
        generateTitleIfNeeded(userMessage);
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        // 不保存 loop_start，避免过度冗余（前端 Attempt 提示仅为运行时辅助）
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        store.saveMessage(sessionId, HistoryMessage.thinking(agentName, content, reasoningContent));
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        String fullArgs = "";
        try {
            fullArgs = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(toolCall.getArguments());
        } catch (Exception e) {
            fullArgs = "{}";
        }
        store.saveMessage(sessionId, HistoryMessage.toolCall(agentName, toolCall.getName(), formattedArgs, fullArgs));
        boolean success = result != null && !result.startsWith("Error:");
        store.saveMessage(sessionId, HistoryMessage.toolResult(agentName, toolCall.getName(), result, success));
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if (maxIte) {
            store.saveMessage(sessionId, HistoryMessage.system(agentName,
                    "[Reached max iterations: " + finalResponse + "]"));
        } else {
            store.saveMessage(sessionId, HistoryMessage.assistant(agentName, finalResponse));
        }
    }

    @Override
    public void onUserMessageInjected(String message) {
        store.saveMessage(sessionId, HistoryMessage.user(agentName, message));
        generateTitleIfNeeded(message);
    }

    private void generateTitleIfNeeded(String userMessage) {
        if (titleGenerated || userMessage == null || userMessage.trim().isEmpty()) return;
        String trimmed = userMessage.trim();
        String title = trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed;
        store.updateTitle(sessionId, title);
        titleGenerated = true;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-core -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-core/src/main/java/com/coloop/agent/core/history/HistoryRecordingHook.java
git commit -m "feat(history): add HistoryRecordingHook to capture lifecycle events"
```

---

### Task 5: WebSocketMessage 扩展

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java`

- [ ] **Step 1: 在 WebSocketMessage.java 末尾（最后一个方法之后）添加两个 factory 方法**

```java
    public static WebSocketMessage historyList(List<SessionMeta> sessions) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SessionMeta s : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.id);
            item.put("title", s.title);
            item.put("createdAt", s.createdAt);
            item.put("updatedAt", s.updatedAt);
            list.add(item);
        }
        payload.put("sessions", list);
        return new WebSocketMessage("history_list", payload);
    }

    public static WebSocketMessage sessionLoaded(String sessionId, String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("title", title);
        return new WebSocketMessage("session_loaded", payload);
    }
```

同时需要在文件顶部添加 import：

```java
import com.coloop.agent.core.history.SessionMeta;
import java.util.ArrayList;
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java
git commit -m "feat(history): add historyList and sessionLoaded WebSocket messages"
```

---

### Task 6: AgentWebSocketHandler 扩展

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java`

- [ ] **Step 1: 在 handleTextMessage 中添加两个新 action**

在现有 `if ("chat".equals(action))` 块之后添加：

```java
            if ("list_history".equals(action)) {
                agentService.listHistory(session);
                return;
            }
            if ("load_session".equals(action)) {
                String sessionId = jsonNode.path("sessionId").asText("");
                if (!sessionId.isEmpty()) {
                    agentService.loadSession(sessionId, session);
                }
                return;
            }
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java
git commit -m "feat(history): add list_history and load_session WebSocket actions"
```

---

### Task 7: AgentService 集成 HistoryStore

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: 添加 import**

```java
import com.coloop.agent.core.history.ConversationHistoryStore;
import com.coloop.agent.core.history.FileSystemHistoryStore;
import com.coloop.agent.core.history.HistoryMessage;
import com.coloop.agent.core.history.HistoryRecordingHook;
import com.coloop.agent.core.history.SessionMeta;
import java.nio.file.Paths;
```

- [ ] **Step 2: 添加 historyStore 字段，修改 SessionContext**

在 `AgentService` 类中，现有字段之后添加：

```java
    private final ConversationHistoryStore historyStore;

    public AgentService() {
        this.historyStore = new FileSystemHistoryStore(Paths.get("."));
    }
```

修改 `SessionContext` 添加 sessionId：

```java
    private static class SessionContext {
        AgentLoop agentLoop;
        boolean isRunning;
        String sessionId;
    }
```

- [ ] **Step 3: 在 startChat 方法中创建 session 并注入 HistoryRecordingHook**

在 `startChat` 方法开头，获取 `SessionContext` 后添加 sessionId 初始化：

```java
    public void startChat(String userMessage, WebSocketSession session) {
        SessionContext ctx = sessions.computeIfAbsent(session.getId(), k -> new SessionContext());

        // Initialize session if new
        if (ctx.sessionId == null) {
            ctx.sessionId = historyStore.createSession();
        }
```

在创建主 AgentLoop 时，注入 `HistoryRecordingHook`：

找到 `WebSocketLoggingHook hook = new WebSocketLoggingHook(session);` 这一行，在其后添加：

```java
                        HistoryRecordingHook historyHook = new HistoryRecordingHook(historyStore, ctx.sessionId, "main");
```

然后在 `main.withHook(hook)` 之前添加：

```java
                        main.withHook(historyHook);
```

- [ ] **Step 4: 在子代理 factory 中注入 HistoryRecordingHook**

在 `SubagentLoopFactory` 的 lambda 中，创建 `SubagentLoggingHook` 之后，添加：

```java
                                    HistoryRecordingHook subHistoryHook = new HistoryRecordingHook(historyStore, ctx.sessionId, name);
```

在 `CapabilityLoader sub = new CapabilityLoader()` 构建时，添加 `.withHook(subHistoryHook)`：

```java
                                    CapabilityLoader sub = new CapabilityLoader()
                                            .withMessageBuilder(subMb)
                                            .withHook(subHook)
                                            .withHook(subHistoryHook);
```

- [ ] **Step 5: 处理 /new 命令时创建新 session**

在 `NewSessionCommand` 的处理中，找到 `ctx.agentLoop = null;` 的地方（在 `CommandExitException` 处理中），在其附近添加 session 重置：

在 `startChat` 方法的 `CommandExitException` 处理块中：

```java
            } catch (CommandExitException e) {
                sendSystem(session, e.getExitMessage());
                synchronized (ctx) {
                    ctx.agentLoop = null;
                    ctx.sessionId = historyStore.createSession(); // 新会话
                }
```

但 `NewSessionCommand` 的逻辑是在 `agentLoop.chatStream` 内部通过拦截器处理的。当 `/new` 被拦截时，`chatStream` 返回成功消息，不会抛 `CommandExitException`。我们需要在 `onLoopEnd` 之后检查是否触发了 `/new`。

更简单的做法：在 `startChat` 方法末尾（`finally` 块中），检查用户消息是否是 `/new`，如果是则创建新 session：

```java
            } finally {
                synchronized (ctx) {
                    ctx.isRunning = false;
                    if ("/new".equals(trimmed) || "/new ".equals(trimmed.substring(0, Math.min(5, trimmed.length())))) {
                        ctx.sessionId = historyStore.createSession();
                    }
                }
            }
```

实际上更好的方式是：修改 `NewSessionCommand`，让它返回一个特殊标记或通过 `CommandContext` 属性通知外部。但为简单起见，我们在 `AgentService` 的 `finally` 中检查：

```java
            } finally {
                synchronized (ctx) {
                    ctx.isRunning = false;
                    if (trimmed.equals("/new")) {
                        ctx.sessionId = historyStore.createSession();
                        sendSystem(session, "New session created.");
                    }
                }
            }
```

- [ ] **Step 6: 添加 listHistory 和 loadSession 方法**

在 `AgentService` 类末尾添加：

```java
    public void listHistory(WebSocketSession session) {
        List<SessionMeta> sessions = historyStore.listSessions();
        try {
            WebSocketMessage msg = WebSocketMessage.historyList(sessions);
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("Failed to send history list: " + e.getMessage());
        }
    }

    public void loadSession(String sessionId, WebSocketSession session) {
        List<HistoryMessage> messages = historyStore.loadMessages(sessionId);
        SessionMeta meta = historyStore.loadSessionMeta(sessionId);
        try {
            for (HistoryMessage hm : messages) {
                WebSocketMessage msg = convertToWebSocketMessage(hm);
                if (msg != null) {
                    String json = objectMapper.writeValueAsString(msg);
                    session.sendMessage(new TextMessage(json));
                }
            }
            WebSocketMessage loadedMsg = WebSocketMessage.sessionLoaded(sessionId, meta != null ? meta.title : "Unknown");
            String json = objectMapper.writeValueAsString(loadedMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("Failed to load session: " + e.getMessage());
        }
    }

    private WebSocketMessage convertToWebSocketMessage(HistoryMessage hm) {
        switch (hm.type) {
            case "user":
                return WebSocketMessage.user(hm.content).withAgent(hm.agent);
            case "assistant":
                return WebSocketMessage.assistant(hm.content).withAgent(hm.agent);
            case "system":
                return WebSocketMessage.system(hm.message).withAgent(hm.agent);
            case "thinking":
                return WebSocketMessage.thinking(hm.content, hm.reasoning).withAgent(hm.agent);
            case "tool_call":
                return WebSocketMessage.toolCall(hm.name, hm.args, hm.fullArgs).withAgent(hm.agent);
            case "tool_result":
                return WebSocketMessage.toolResult(hm.name, hm.result, hm.success != null ? hm.success : true).withAgent(hm.agent);
            case "subagent_created":
                return WebSocketMessage.subagentCreated(hm.agent, hm.description, null).withAgent(hm.agent);
            default:
                return null;
        }
    }
```

- [ ] **Step 7: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java
git commit -m "feat(history): integrate ConversationHistoryStore into AgentService"
```

---

### Task 8: 前端 index.html 侧边栏重构

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/index.html`

- [ ] **Step 1: 替换 sidebar 结构**

将现有的 `<aside class="agent-sidebar" id="agent-sidebar">` 及其内部内容替换为：

```html
        <aside class="agent-sidebar" id="agent-sidebar">
            <div class="agent-sidebar-header">
                <span class="agent-sidebar-title">coloop</span>
                <span class="agent-sidebar-toggle" id="agent-sidebar-toggle" title="收起/展开">◀</span>
            </div>
            <div class="sidebar-sections">
                <!-- 当前会话 -->
                <div class="sidebar-section expanded" data-section="current">
                    <div class="sidebar-section-header">
                        <span class="section-toggle">▼</span>
                        <span class="section-title">当前会话</span>
                    </div>
                    <div class="sidebar-section-body" id="current-session-list">
                        <div class="agent-item active" data-agent="main">
                            <span class="agent-icon">⭐</span>
                            <span class="agent-name">main</span>
                        </div>
                    </div>
                </div>
                <!-- 历史记录 -->
                <div class="sidebar-section" data-section="history">
                    <div class="sidebar-section-header" id="history-section-header">
                        <span class="section-toggle">▶</span>
                        <span class="section-title">历史记录</span>
                    </div>
                    <div class="sidebar-section-body collapsed" id="history-list">
                        <!-- 历史会话动态插入 -->
                    </div>
                </div>
            </div>
        </aside>
```

- [ ] **Step 2: 在 `<style>` 标签内添加 sidebar section 样式**

在现有 `.agent-list::-webkit-scrollbar-thumb` 规则之后、`.main-content` 规则之前添加：

```css
        .sidebar-sections {
            flex: 1;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
        }
        .sidebar-section {
            display: flex;
            flex-direction: column;
        }
        .sidebar-section-header {
            padding: 10px 14px;
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            font-size: 12px;
            font-weight: 600;
            color: var(--sidebar-section-header-color, #8b7aa0);
            text-transform: uppercase;
            letter-spacing: 0.5px;
            user-select: none;
            transition: background 0.15s ease;
        }
        .sidebar-section-header:hover {
            background: var(--sidebar-section-header-hover-bg, #ede9f7);
        }
        .section-toggle {
            font-size: 10px;
            transition: transform 0.2s ease;
        }
        .sidebar-section.expanded .section-toggle {
            transform: rotate(0deg);
        }
        .sidebar-section:not(.expanded) .section-toggle {
            transform: rotate(-90deg);
        }
        .sidebar-section-body {
            display: flex;
            flex-direction: column;
            padding: 0 8px 8px;
            gap: 2px;
        }
        .sidebar-section-body.collapsed {
            display: none;
        }
        .history-item {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 7px 10px;
            border-radius: 8px;
            font-size: 12px;
            line-height: 1.4;
            color: var(--sidebar-history-item-color, #555);
            cursor: pointer;
            transition: all 0.15s ease;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .history-item:hover {
            background: var(--sidebar-history-item-hover-bg, #ede9f7);
            color: var(--sidebar-history-item-hover-color, #4a306d);
        }
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/index.html
git commit -m "feat(history): refactor sidebar to accordion with current session and history sections"
```

---

### Task 9: 前端 chat.js 历史功能

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: 在 IIFE 顶部添加新元素引用和状态变量**

在 `const agentSidebarToggleEl = ...` 之后添加：

```javascript
    const historySectionHeader = document.getElementById('history-section-header');
    const historyListEl = document.getElementById('history-list');
    const currentSessionListEl = document.getElementById('current-session-list');
    let currentSessionId = null;
    let isLoadingHistory = false;
```

- [ ] **Step 2: 修改 ensureAgent 和 switchToAgent，将 agent 项插入 current-session-list**

修改 `ensureAgent` 函数，在创建新 agent state 时，如果 agent 不是 main 且 sidebar 中没有对应项，自动添加到 current-session-list：

```javascript
    function ensureAgent(name, meta) {
        if (!agentState.has(name)) {
            agentState.set(name, {
                fragment: document.createDocumentFragment(),
                currentAssistantEl: null,
                streamBuffer: '',
                lastRenderTime: 0,
                streamRenderTimer: null,
                contextUsage: null,
                meta: meta || { name: name }
            });
            if (name !== 'main' && currentSessionListEl) {
                addAgentToCurrentSession(name, meta);
            }
        }
    }
```

修改 `switchToAgent`：现有逻辑不变，但更新 currentSessionListEl 中的 active 状态。

添加 `addAgentToCurrentSession` 函数：

```javascript
    function addAgentToCurrentSession(name, meta) {
        if (!currentSessionListEl) return;
        var existing = currentSessionListEl.querySelector('[data-agent="' + name + '"]');
        if (existing) return;
        var item = document.createElement('div');
        item.className = 'agent-item';
        item.dataset.agent = name;
        item.innerHTML = '<span class="agent-icon">🤖</span><span class="agent-name">' + escapeHtml(name) + '</span>';
        item.addEventListener('click', function() {
            switchToAgent(name);
        });
        currentSessionListEl.appendChild(item);
    }
```

修改 `addAgentToSidebar` 函数为 `addAgentToCurrentSession`（重命名现有引用）。

实际上，现有代码中 `addAgentToSidebar` 就是添加 agent 到 sidebar 的。我们需要修改它，让它插入到 `currentSessionListEl` 而不是 `agentListEl`：

将 `addAgentToSidebar` 函数修改为：

```javascript
    function addAgentToSidebar(payload) {
        addAgentToCurrentSession(payload.name, { name: payload.name, description: payload.description });
    }
```

将 `removeAgentFromSidebar` 修改为从 `currentSessionListEl` 中移除：

```javascript
    function removeAgentFromSidebar(name) {
        if (!currentSessionListEl) return;
        var item = currentSessionListEl.querySelector('[data-agent="' + name + '"]');
        if (!item) return;
        item.classList.add('removing');
        setTimeout(function() {
            if (item.parentNode) item.parentNode.removeChild(item);
        }, 300);
    }
```

- [ ] **Step 3: 在 handleMessage 中处理 history_list 和 session_loaded**

在 `handleMessage` 的 switch 语句中添加两个 case：

```javascript
            case 'history_list':
                renderHistoryList(msg.payload && msg.payload.sessions);
                return;
            case 'session_loaded':
                isLoadingHistory = false;
                renderSystem('Loaded session: ' + (msg.payload.title || msg.payload.sessionId));
                return;
```

添加 `renderHistoryList` 函数：

```javascript
    function renderHistoryList(sessions) {
        if (!historyListEl) return;
        historyListEl.innerHTML = '';
        if (!sessions || !sessions.length) {
            var empty = document.createElement('div');
            empty.className = 'history-item';
            empty.style.opacity = '0.5';
            empty.textContent = '无历史记录';
            historyListEl.appendChild(empty);
            return;
        }
        sessions.forEach(function(s) {
            var item = document.createElement('div');
            item.className = 'history-item';
            item.dataset.sessionId = s.id;
            item.textContent = s.title || s.id;
            item.title = new Date(s.createdAt).toLocaleString();
            item.addEventListener('click', function() {
                loadSession(s.id);
            });
            historyListEl.appendChild(item);
        });
    }
```

添加 `loadSession` 函数：

```javascript
    function loadSession(sessionId) {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        isLoadingHistory = true;
        // Clear current view
        agentState.clear();
        chatContainer.innerHTML = '';
        ensureAgent('main', { name: 'main' });
        currentAgent = 'main';
        updateSidebarActive('main');
        // Clear current session list except main
        if (currentSessionListEl) {
            var items = currentSessionListEl.querySelectorAll('.agent-item');
            items.forEach(function(item) {
                if (item.dataset.agent !== 'main') item.remove();
            });
        }
        ws.send(JSON.stringify({ action: 'load_session', sessionId: sessionId }));
    }
```

- [ ] **Step 4: 添加历史记录 section 展开/收起交互**

在 IIFE 末尾（connect() 调用之前）添加：

```javascript
    // History section toggle
    if (historySectionHeader) {
        historySectionHeader.addEventListener('click', function() {
            var section = historySectionHeader.closest('.sidebar-section');
            var body = document.getElementById('history-list');
            if (section.classList.contains('expanded')) {
                section.classList.remove('expanded');
                body.classList.add('collapsed');
            } else {
                section.classList.add('expanded');
                body.classList.remove('collapsed');
                // Request history list when expanding
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ action: 'list_history' }));
                }
            }
        });
    }

    // Current session section toggle
    var currentSectionHeader = document.querySelector('[data-section="current"] .sidebar-section-header');
    if (currentSectionHeader) {
        currentSectionHeader.addEventListener('click', function() {
            var section = currentSectionHeader.closest('.sidebar-section');
            var body = section.querySelector('.sidebar-section-body');
            if (section.classList.contains('expanded')) {
                section.classList.remove('expanded');
                body.classList.add('collapsed');
            } else {
                section.classList.add('expanded');
                body.classList.remove('collapsed');
            }
        });
    }
```

- [ ] **Step 5: 修改 sendMessage，在发送时检查是否是 /new**

不需要特别处理，`/new` 作为普通消息发送即可。

- [ ] **Step 6: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(history): add history list, session loading, and accordion sidebar interactions"
```

---

### Task 10: CSS 主题变量

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/themes/claude.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/cursor.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/discord.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/terminal.css`

- [ ] **Step 1: 在每个主题的 `:root` 中添加 sidebar section 变量**

**claude.css** 的 `:root` 中添加：

```css
  --sidebar-section-header-color: #8b7aa0;
  --sidebar-section-header-hover-bg: #ede9f7;
  --sidebar-history-item-color: #555;
  --sidebar-history-item-hover-bg: #ede9f7;
  --sidebar-history-item-hover-color: #4a306d;
```

**cursor.css** 的 `:root` 中添加：

```css
  --sidebar-section-header-color: #6e7681;
  --sidebar-section-header-hover-bg: #21262d;
  --sidebar-history-item-color: #c9d1d9;
  --sidebar-history-item-hover-bg: #21262d;
  --sidebar-history-item-hover-color: #58a6ff;
```

**discord.css** 的 `:root` 中添加：

```css
  --sidebar-section-header-color: #96989d;
  --sidebar-section-header-hover-bg: #36373d;
  --sidebar-history-item-color: #b5bac1;
  --sidebar-history-item-hover-bg: #36373d;
  --sidebar-history-item-hover-color: #fff;
```

**terminal.css** 的 `:root` 中添加：

```css
  --sidebar-section-header-color: #6c6c6c;
  --sidebar-section-header-hover-bg: #262626;
  --sidebar-history-item-color: #bbbbbb;
  --sidebar-history-item-hover-bg: #262626;
  --sidebar-history-item-hover-color: #00ff00;
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/themes/
git commit -m "feat(history): add sidebar section CSS variables to all themes"
```

---

### Task 11: 编译和运行验证

- [ ] **Step 1: 编译完整项目**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行服务器并手动验证**

```bash
mvn -pl coloop-agent-server spring-boot:run
```

验证清单：
1. 打开浏览器访问 `http://localhost:8080`
2. 发送一条消息，检查 `.history/` 目录是否生成
3. 检查 `.history/<sessionId>/messages.json` 是否包含用户消息和助手回复
4. 展开"历史记录"，检查是否显示会话标题
5. 点击历史会话，检查是否正确加载
6. 创建子代理，检查子代理消息是否也保存在同一个 messages.json 中
7. 执行 `/new`，检查是否创建新会话

- [ ] **Step 3: Commit 最终版本**

```bash
git add -A
git commit -m "feat(history): complete long-term memory module with session persistence and history sidebar"
```

---

## 自检

1. **Spec coverage:**
   - [x] `.history/` 文件夹 + gitignore → Task 1
   - [x] 侧边栏一级二级菜单 → Task 8, 9
   - [x] 当前会话默认展开 → Task 8 (`.sidebar-section.expanded`)
   - [x] 历史记录作为第二个一级菜单 → Task 8
   - [x] 会话名称生成（第一条消息前30字）→ Task 4 (`generateTitleIfNeeded`)
   - [x] 主代理和子代理聊天记录存储 → Task 4, 7 (HistoryRecordingHook 注入主 loop 和 sub loop)
   - [x] 点击加载历史会话 → Task 6, 7, 9 (`load_session` action)
   - [x] 更新当前会话菜单内容 → Task 9 (`loadSession` 清空并重建)

2. **Placeholder scan:** 无 TBD/TODO/"implement later"

3. **Type consistency:** `HistoryMessage.type`, `SessionMeta.id` 等字段在 store、hook、前端中一致。`WebSocketMessage` 的 `withAgent` 方法在 `convertToWebSocketMessage` 中正确使用。
