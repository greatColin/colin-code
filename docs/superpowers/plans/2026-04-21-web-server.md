# Web Server 模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 coloop-agent 拆分为多模块 Maven 项目，新增基于 Spring Boot 的 Web 服务模块，提供 WebSocket 实时聊天界面替代 CLI 入口。

**Architecture:** `core` 模块保持零 Spring 依赖的教学骨架，`server` 模块作为独立 Spring Boot 应用依赖 `core`。WebSocket 推送结构化日志事件，前端原生 JS 按类型渲染不同样式。

**Tech Stack:** Maven, JDK 17, Spring Boot 3.2.5, Spring WebSocket, Jackson, native HTML/CSS/JS

---

## 文件结构总览

```
coloop-agent/
├── pom.xml                                    ← parent POM（聚合，packaging=pom）
├── coloop-agent-core/                         ← 现有代码迁移至此
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/coloop/agent/        ← 现有所有 Java 代码
│       ├── main/resources/coloop-agent-setting.json
│       └── test/java/                         ← 现有所有测试
└── coloop-agent-server/                       ← 新模块
    ├── pom.xml
    └── src/main/
        ├── java/com/coloop/agent/server/
        │   ├── ServerApplication.java
        │   ├── config/AgentWebConfig.java
        │   ├── controller/ChatController.java
        │   ├── websocket/AgentWebSocketHandler.java
        │   ├── service/AgentService.java
        │   ├── hook/WebSocketLoggingHook.java
        │   └── dto/WebSocketMessage.java
        └── resources/
            ├── application.properties
            └── static/
                ├── index.html
                ├── chat.css
                └── chat.js
```

---

## Task 1: 多模块 Maven 项目拆分

**目标:** 将单模块项目拆分为 `coloop-agent-core` + `coloop-agent-server` 两个模块。

**Files:**
- Modify: `pom.xml`
- Create: `coloop-agent-core/pom.xml`
- Create: `coloop-agent-server/pom.xml`
- Move: `src/` → `coloop-agent-core/src/`

- [ ] **Step 1: 备份现有 pom.xml**

```bash
cp pom.xml pom.xml.backup
```

- [ ] **Step 2: 重写根 pom.xml 为 parent 聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.coloop</groupId>
    <artifactId>coloop-agent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>coloop-agent</name>
    <description>可插拔、模块隔离的轻量级 AGI Agent 底座</description>

    <modules>
        <module>coloop-agent-core</module>
        <module>coloop-agent-server</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jackson.version>2.15.3</jackson.version>
        <okhttp.version>4.12.0</okhttp.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>com.squareup.okhttp3</groupId>
                <artifactId>okhttp</artifactId>
                <version>${okhttp.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: 移动现有源码到 core 模块目录**

```bash
mkdir -p coloop-agent-core
# 移动 src 到 core 模块
git mv src coloop-agent-core/
```

- [ ] **Step 4: 创建 coloop-agent-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.coloop</groupId>
        <artifactId>coloop-agent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>coloop-agent-core</artifactId>
    <packaging>jar</packaging>
    <name>coloop-agent-core</name>
    <description>Agent 核心骨架（教学层 + 能力层 + 运行时层）</description>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: 创建 coloop-agent-server/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.coloop</groupId>
        <artifactId>coloop-agent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>coloop-agent-server</artifactId>
    <packaging>jar</packaging>
    <name>coloop-agent-server</name>
    <description>Spring Boot Web 服务模块（WebSocket + 聊天界面）</description>

    <dependencies>
        <!-- core 模块 -->
        <dependency>
            <groupId>com.coloop</groupId>
            <artifactId>coloop-agent-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 6: 编译验证多模块结构**

```bash
mvn clean compile -pl coloop-agent-core
```

Expected: BUILD SUCCESS，core 模块编译通过。

- [ ] **Step 7: 安装 core 到本地仓库（供 server 依赖）**

```bash
mvn install -pl coloop-agent-core -DskipTests
```

Expected: BUILD SUCCESS，core 安装到本地 Maven 仓库。

- [ ] **Step 8: Commit**

```bash
git add pom.xml coloop-agent-core/ coloop-agent-server/
git commit -m "refactor: 拆分为多模块 Maven 项目（core + server）"
```

---

## Task 2: WebSocket DTO 与消息协议

**目标:** 定义 WebSocket 结构化消息 DTO。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/dto/WebSocketMessage.java`

- [ ] **Step 1: 创建 WebSocketMessage DTO**

```java
package com.coloop.agent.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    private String type;
    private Map<String, Object> payload;
    private long timestamp;

    public WebSocketMessage() {}

    public WebSocketMessage(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public static WebSocketMessage user(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        return new WebSocketMessage("user", payload);
    }

    public static WebSocketMessage loopStart(int attempt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attempt", attempt);
        return new WebSocketMessage("loop_start", payload);
    }

    public static WebSocketMessage thinking(String content, String reasoning) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("reasoning", reasoning);
        return new WebSocketMessage("thinking", payload);
    }

    public static WebSocketMessage toolCall(String name, String args, String fullArgs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("args", args);
        payload.put("fullArgs", fullArgs);
        return new WebSocketMessage("tool_call", payload);
    }

    public static WebSocketMessage toolResult(String name, String result, boolean success) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("result", result);
        payload.put("success", success);
        return new WebSocketMessage("tool_result", payload);
    }

    public static WebSocketMessage assistant(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        return new WebSocketMessage("assistant", payload);
    }

    public static WebSocketMessage system(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return new WebSocketMessage("system", payload);
    }

    public static WebSocketMessage error(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return new WebSocketMessage("error", payload);
    }

    // Getters & Setters (required for Jackson serialization)
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/dto/
git commit -m "feat(server): 添加 WebSocket 结构化消息 DTO"
```

---

## Task 3: WebSocketLoggingHook

**目标:** 实现 AgentHook，将生命周期事件转为 JSON 推送到 WebSocket Session。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/hook/WebSocketLoggingHook.java`

- [ ] **Step 1: 创建 WebSocketLoggingHook**

```java
package com.coloop.agent.server.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketLoggingHook implements AgentHook {
    private final WebSocketSession session;
    private final ObjectMapper mapper;
    private final AtomicInteger loopCount = new AtomicInteger(0);

    public WebSocketLoggingHook(WebSocketSession session) {
        this.session = session;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void onLoopStart(String userMessage) {
        loopCount.set(0);
        send(WebSocketMessage.user(userMessage));
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        int count = loopCount.incrementAndGet();
        send(WebSocketMessage.loopStart(count));
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        send(WebSocketMessage.thinking(content, reasoningContent));
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        send(WebSocketMessage.toolCall(toolCall.getName(), formattedArgs, toolCall.getArguments()));
        boolean success = result != null && !result.startsWith("Error:");
        send(WebSocketMessage.toolResult(toolCall.getName(), result, success));
    }

    @Override
    public void onLoopEnd(boolean maxIte, String finalResponse) {
        if (maxIte) {
            send(WebSocketMessage.system(finalResponse));
        } else {
            send(WebSocketMessage.assistant(finalResponse));
        }
    }

    private void send(WebSocketMessage msg) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            System.err.println("[WebSocketLoggingHook] Failed to send message: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/hook/
git commit -m "feat(server): 实现 WebSocketLoggingHook，将 AgentHook 事件推送到 WebSocket"
```

---

## Task 4: AgentService

**目标:** 管理 AgentLoop 生命周期，在后台线程运行 chat。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: 创建 AgentService**

```java
package com.coloop.agent.server.service;

import com.coloop.agent.capability.provider.openai.OpenAICompatibleProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;
import com.coloop.agent.server.hook.WebSocketLoggingHook;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentService {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void startChat(String userMessage, WebSocketSession session) {
        executor.submit(() -> {
            try {
                AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
                LLMProvider provider = new OpenAICompatibleProvider(config.getModelConfig("minimax"));
                WebSocketLoggingHook hook = new WebSocketLoggingHook(session);

                AgentLoop agentLoop = new CapabilityLoader()
                        .withCapability(StandardCapability.EXEC_TOOL, config)
                        .withCapability(StandardCapability.READ_FILE_TOOL, config)
                        .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
                        .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
                        .withCapability(StandardCapability.SEARCH_FILES_TOOL, config)
                        .withCapability(StandardCapability.LIST_DIRECTORY_TOOL, config)
                        .withCapability(StandardCapability.BASE_PROMPT, config)
                        .withCapability(StandardCapability.AGENTS_MD_PROMPT, config)
                        .withCapability(StandardCapability.LOGGING_HOOK, config)
                        .withHook(hook)
                        .build(provider, config);

                agentLoop.chat(userMessage);
            } catch (Exception e) {
                sendError(session, e.getMessage());
            }
        });
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                com.coloop.agent.server.dto.WebSocketMessage errorMsg =
                        com.coloop.agent.server.dto.WebSocketMessage.error(
                                message != null ? message : "Unknown error"
                        );
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorMsg);
                session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            }
        } catch (Exception ignored) {}
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/
git commit -m "feat(server): 添加 AgentService，后台线程运行 AgentLoop"
```

---

## Task 5: WebSocket Handler

**目标:** 处理 WebSocket 连接、接收前端消息、调用 AgentService。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java`

- [ ] **Step 1: 创建 AgentWebSocketHandler**

```java
package com.coloop.agent.server.websocket;

import com.coloop.agent.server.service.AgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {
    private final AgentService agentService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentWebSocketHandler(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("[WebSocket] Connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = mapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "";
            if ("chat".equals(action)) {
                String userMessage = json.has("message") ? json.get("message").asText() : "";
                if (!userMessage.isEmpty()) {
                    agentService.startChat(userMessage, session);
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] Failed to handle message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("[WebSocket] Disconnected: " + session.getId() + " status=" + status);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/
git commit -m "feat(server): 添加 WebSocket 处理器，接收前端聊天消息"
```

---

## Task 6: WebSocket 配置

**目标:** 注册 WebSocket handler，映射 `/ws/agent` 路径。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/config/AgentWebConfig.java`

- [ ] **Step 1: 创建 AgentWebConfig**

```java
package com.coloop.agent.server.config;

import com.coloop.agent.server.websocket.AgentWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AgentWebConfig implements WebSocketConfigurer {
    private final AgentWebSocketHandler agentWebSocketHandler;

    public AgentWebConfig(AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/config/
git commit -m "feat(server): 注册 WebSocket handler，映射 /ws/agent 路径"
```

---

## Task 7: REST Controller

**目标:** 提供健康检查等 REST 端点。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/controller/ChatController.java`

- [ ] **Step 1: 创建 ChatController**

```java
package com.coloop.agent.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ChatController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "coloop-agent-server");
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/controller/
git commit -m "feat(server): 添加健康检查 REST 端点 /api/health"
```

---

## Task 8: Spring Boot 入口

**目标:** 创建 Spring Boot 应用入口。

**Files:**
- Create: `coloop-agent-server/src/main/java/com/coloop/agent/server/ServerApplication.java`
- Create: `coloop-agent-server/src/main/resources/application.properties`

- [ ] **Step 1: 创建 ServerApplication**

```java
package com.coloop.agent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 application.properties**

```properties
# Server
server.port=8080

# Logging
logging.level.org.springframework.web.socket=DEBUG
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl coloop-agent-server -am
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/ServerApplication.java \
        coloop-agent-server/src/main/resources/application.properties
git commit -m "feat(server): 添加 Spring Boot 入口和配置文件"
```

---

## Task 9: 前端 HTML 页面

**目标:** 创建聊天界面 HTML 骨架。

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/index.html`

- [ ] **Step 1: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>coloop-agent Web</title>
    <link rel="stylesheet" href="chat.css">
</head>
<body>
    <div class="app">
        <header class="header">
            <h1>coloop-agent Web</h1>
            <span id="connection-status" class="status connecting">连接中...</span>
        </header>
        <main class="chat-container" id="chat-container">
            <!-- 消息将动态插入这里 -->
        </main>
        <footer class="input-area">
            <textarea id="message-input" placeholder="输入消息... (Shift+Enter 换行, Enter 发送)" rows="2"></textarea>
            <button id="send-btn" disabled>发送</button>
        </footer>
    </div>
    <script src="chat.js"></script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/index.html
git commit -m "feat(web): 添加聊天界面 HTML 骨架"
```

---

## Task 10: 前端 CSS 样式

**目标:** 创建聊天界面样式，模仿 Claude.ai 风格。

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/chat.css`

- [ ] **Step 1: 创建 chat.css**

```css
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #f5f5f5;
    height: 100vh;
    overflow: hidden;
}

.app {
    display: flex;
    flex-direction: column;
    height: 100vh;
    max-width: 900px;
    margin: 0 auto;
    background: #fff;
    box-shadow: 0 0 20px rgba(0,0,0,0.1);
}

/* Header */
.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 20px;
    border-bottom: 1px solid #e5e7eb;
    background: #fff;
}

.header h1 {
    font-size: 18px;
    font-weight: 600;
    color: #111827;
}

.status {
    font-size: 12px;
    padding: 4px 10px;
    border-radius: 12px;
    font-weight: 500;
}

.status.connecting { background: #fef3c7; color: #92400e; }
.status.connected { background: #d1fae5; color: #065f46; }
.status.disconnected { background: #fee2e2; color: #991b1b; }

/* Chat Container */
.chat-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

/* Message base */
.message {
    max-width: 85%;
    padding: 12px 16px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
}

/* User message */
.message.user {
    align-self: flex-end;
    background: #2563eb;
    color: #fff;
}

/* Assistant message */
.message.assistant {
    align-self: flex-start;
    background: #f3f4f6;
    color: #111827;
    border: 1px solid #e5e7eb;
}

/* Loop start */
.message.loop-start {
    align-self: center;
    background: transparent;
    color: #9ca3af;
    font-size: 12px;
    padding: 4px 12px;
}

/* System message */
.message.system {
    align-self: center;
    background: transparent;
    color: #f59e0b;
    font-size: 12px;
    padding: 4px 12px;
}

/* Error message */
.message.error {
    align-self: center;
    background: transparent;
    color: #ef4444;
    font-size: 12px;
    padding: 4px 12px;
}

/* Thinking card */
.card.thinking {
    align-self: flex-start;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    overflow: hidden;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    background: #f3f4f6;
    cursor: pointer;
    user-select: none;
}

.card-header:hover {
    background: #e5e7eb;
}

.card-title {
    font-size: 12px;
    font-weight: 600;
    color: #6b7280;
}

.card-toggle {
    font-size: 12px;
    color: #9ca3af;
}

.card-body {
    padding: 12px;
    font-size: 13px;
    color: #374151;
    white-space: pre-wrap;
    font-family: monospace;
    max-height: 400px;
    overflow-y: auto;
}

.card-body.collapsed {
    display: none;
}

/* Tool call card */
.card.tool-call {
    align-self: flex-start;
    background: #fef3c7;
    border: 1px solid #fcd34d;
    border-radius: 8px;
    overflow: hidden;
}

.card.tool-call .card-header {
    background: #fde68a;
}

.card.tool-call .card-header:hover {
    background: #fcd34d;
}

.card.tool-call .card-title {
    color: #92400e;
}

/* Tool result card */
.card.tool-result {
    align-self: flex-start;
    background: #1f2937;
    border: 1px solid #374151;
    border-radius: 8px;
    overflow: hidden;
}

.card.tool-result .card-header {
    background: #374151;
}

.card.tool-result .card-header:hover {
    background: #4b5563;
}

.card.tool-result .card-title {
    color: #10b981;
}

.card.tool-result .card-body {
    color: #e5e7eb;
    background: #1f2937;
}

/* Input area */
.input-area {
    display: flex;
    gap: 8px;
    padding: 12px 20px;
    border-top: 1px solid #e5e7eb;
    background: #fff;
}

.input-area textarea {
    flex: 1;
    padding: 10px 14px;
    border: 1px solid #d1d5db;
    border-radius: 8px;
    font-size: 14px;
    resize: none;
    outline: none;
}

.input-area textarea:focus {
    border-color: #2563eb;
}

.input-area textarea:disabled {
    background: #f3f4f6;
    color: #9ca3af;
}

.input-area button {
    padding: 10px 20px;
    background: #2563eb;
    color: #fff;
    border: none;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
}

.input-area button:hover:not(:disabled) {
    background: #1d4ed8;
}

.input-area button:disabled {
    background: #9ca3af;
    cursor: not-allowed;
}
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.css
git commit -m "feat(web): 添加聊天界面 CSS 样式"
```

---

## Task 11: 前端 JavaScript

**目标:** 实现 WebSocket 连接、消息接收、UI 渲染、用户输入发送。

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: 创建 chat.js**

```javascript
(function() {
    const chatContainer = document.getElementById('chat-container');
    const messageInput = document.getElementById('message-input');
    const sendBtn = document.getElementById('send-btn');
    const statusEl = document.getElementById('connection-status');

    const wsUrl = 'ws://' + window.location.host + '/ws/agent';
    let ws = null;
    let reconnectTimer = null;

    function connect() {
        updateStatus('connecting', '连接中...');
        ws = new WebSocket(wsUrl);

        ws.onopen = function() {
            updateStatus('connected', '已连接');
            enableInput(true);
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        };

        ws.onmessage = function(event) {
            try {
                const msg = JSON.parse(event.data);
                handleMessage(msg);
            } catch (e) {
                console.error('Failed to parse message:', e);
            }
        };

        ws.onclose = function() {
            updateStatus('disconnected', '已断开');
            enableInput(false);
            reconnectTimer = setTimeout(connect, 3000);
        };

        ws.onerror = function(err) {
            console.error('WebSocket error:', err);
        };
    }

    function updateStatus(clazz, text) {
        statusEl.className = 'status ' + clazz;
        statusEl.textContent = text;
    }

    function enableInput(enabled) {
        messageInput.disabled = !enabled;
        sendBtn.disabled = !enabled;
    }

    function handleMessage(msg) {
        switch (msg.type) {
            case 'user':
                renderUser(msg.payload.content);
                break;
            case 'loop_start':
                renderLoopStart(msg.payload.attempt);
                break;
            case 'thinking':
                renderThinking(msg.payload);
                break;
            case 'tool_call':
                renderToolCall(msg.payload);
                break;
            case 'tool_result':
                renderToolResult(msg.payload);
                break;
            case 'assistant':
                renderAssistant(msg.payload.content);
                break;
            case 'system':
                renderSystem(msg.payload.message);
                break;
            case 'error':
                renderError(msg.payload.message);
                break;
        }
        scrollToBottom();
    }

    function appendElement(el) {
        chatContainer.appendChild(el);
    }

    function scrollToBottom() {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    function renderUser(content) {
        const el = document.createElement('div');
        el.className = 'message user';
        el.textContent = content;
        appendElement(el);
    }

    function renderAssistant(content) {
        const el = document.createElement('div');
        el.className = 'message assistant';
        el.textContent = content;
        appendElement(el);
    }

    function renderLoopStart(attempt) {
        const el = document.createElement('div');
        el.className = 'message loop-start';
        el.textContent = '▶ Attempt ' + attempt + '...';
        appendElement(el);
    }

    function renderSystem(message) {
        const el = document.createElement('div');
        el.className = 'message system';
        el.textContent = message;
        appendElement(el);
    }

    function renderError(message) {
        const el = document.createElement('div');
        el.className = 'message error';
        el.textContent = '⚠ ' + message;
        appendElement(el);
    }

    function renderThinking(payload) {
        let content = '';
        if (payload.reasoning) {
            content += '[REASONING]\n' + payload.reasoning + '\n\n';
        }
        if (payload.content) {
            content += '[THINK]\n' + payload.content;
        }
        renderCard('thinking', '💭 Thinking', content);
    }

    function renderToolCall(payload) {
        let content = 'Name: ' + payload.name + '\n';
        if (payload.fullArgs) {
            content += 'Args:\n' + payload.fullArgs;
        } else if (payload.args) {
            content += 'Args:\n' + payload.args;
        }
        renderCard('tool-call', '🔧 ' + payload.name, content);
    }

    function renderToolResult(payload) {
        renderCard('tool-result', '✅ Result: ' + payload.name, payload.result || '');
    }

    function renderCard(type, title, bodyContent) {
        const card = document.createElement('div');
        card.className = 'card ' + type;

        const header = document.createElement('div');
        header.className = 'card-header';

        const titleEl = document.createElement('span');
        titleEl.className = 'card-title';
        titleEl.textContent = title;

        const toggle = document.createElement('span');
        toggle.className = 'card-toggle';
        toggle.textContent = '▼';

        header.appendChild(titleEl);
        header.appendChild(toggle);

        const body = document.createElement('div');
        body.className = 'card-body';
        body.textContent = bodyContent;

        // 默认折叠
        body.classList.add('collapsed');
        toggle.textContent = '▶';

        header.addEventListener('click', function() {
            body.classList.toggle('collapsed');
            toggle.textContent = body.classList.contains('collapsed') ? '▶' : '▼';
        });

        card.appendChild(header);
        card.appendChild(body);
        appendElement(card);
    }

    function sendMessage() {
        const text = messageInput.value.trim();
        if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;

        ws.send(JSON.stringify({ action: 'chat', message: text }));
        messageInput.value = '';
    }

    sendBtn.addEventListener('click', sendMessage);

    messageInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // 启动连接
    connect();
})();
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(web): 添加前端 JS，WebSocket 连接与消息渲染"
```

---

## Task 12: 编译与运行验证

**目标:** 确保整个项目编译通过，Spring Boot 应用可以启动。

- [ ] **Step 1: 完整编译**

```bash
mvn clean compile -am
```

Expected: BUILD SUCCESS for both core and server modules.

- [ ] **Step 2: 安装 core 后编译 server**

```bash
mvn install -DskipTests
```

Expected: BUILD SUCCESS，两个模块都安装到本地仓库。

- [ ] **Step 3: 运行 Spring Boot 应用（前台测试）**

```bash
cd coloop-agent-server
mvn spring-boot:run
```

Expected: Spring Boot 启动成功，监听 8080 端口，日志显示 "Started ServerApplication"。

- [ ] **Step 4: 验证静态页面可访问**

在浏览器打开 `http://localhost:8080/`，应看到聊天界面。

- [ ] **Step 5: 验证 WebSocket 端点**

浏览器 DevTools Console 中应显示 WebSocket 连接成功（`▶ Attempt 1...` 之前的连接状态变为"已连接"）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(server): Web 服务模块完整实现，支持 WebSocket 实时聊天"
```

---

## 自检清单

### Spec 覆盖度

| 设计文档要求 | 对应 Task |
|-------------|----------|
| 多模块 Maven 结构 (core + server) | Task 1 |
| core 零 Spring 依赖 | Task 1 (core pom 无 Spring) |
| WebSocket 消息协议 DTO | Task 2 |
| 8 种消息类型 | Task 2 (user, loop_start, thinking, tool_call, tool_result, assistant, system, error) |
| WebSocketLoggingHook 实现 AgentHook | Task 3 |
| AgentService 后台线程运行 AgentLoop | Task 4 |
| WebSocket Handler | Task 5 |
| WebSocket 配置 /ws/agent | Task 6 |
| REST 健康检查 | Task 7 |
| Spring Boot 入口 | Task 8 |
| 前端 HTML 骨架 | Task 9 |
| 前端 CSS 样式（Claude.ai 风格） | Task 10 |
| 前端 JS（WebSocket + 消息渲染 + 折叠展开） | Task 11 |
| 所有数据完整无截断 | Task 11 (前端显示完整 content) |
| 折叠/展开交互 | Task 10 + 11 |

### Placeholder 扫描

- 无 "TBD", "TODO", "implement later"
- 无 "Add appropriate error handling" 等模糊描述
- 每个任务包含完整代码和命令
- 无 "Similar to Task N" 引用

### 类型一致性

- `WebSocketMessage` 的 factory 方法命名和字段与 `WebSocketLoggingHook` 中的使用一致
- `AgentHook` 方法签名与 core 模块中的定义一致
- `CapabilityLoader` 和 `StandardCapability` 的使用方式与 `CliApp` 一致
