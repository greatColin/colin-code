# Agent Graph View 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增「复合模式」：左侧拓扑图展示 agent 关系与工作状态，右侧群聊展示过滤后的 agent 最终发言。支持拓扑折叠、三种布局切换、直接向子 agent 发消息。

**Architecture:** 后端扩展 WebSocket `targetAgent` 字段，`SessionContext` 持久化 `SubagentRegistry`。前端采用统一 `GraphState` 驱动左右分栏的拓扑画布和群聊消息区，单聊模式保持现有行为。

**Tech Stack:** Spring Boot WebSocket, Vanilla JS, CSS, SVG

---

## 文件映射

| 文件 | 职责 |
|------|------|
| `AgentWebSocketHandler.java` | 解析 `targetAgent` 字段，路由到 `startChat` 或 `sendToSubagent` |
| `AgentService.java` | 新增 `sendToSubagent()`，`SessionContext` 持久化 `SubagentRegistry` |
| `index.html` | 顶部模式切换器、复合模式左右分栏容器 |
| `chat.js` | 同步 WS 消息到 `GraphState`，模式切换逻辑 |
| `graph-state.js` | 统一状态层：agent 状态、群聊消息、拓扑坐标 |
| `group-chat.js` | 群聊模式渲染：消息气泡、成员列表、输入框+发给下拉框 |
| `topology.js` | 拓扑模式主控：画布、布局切换、AgentNode 渲染、SVG 连线 |
| `layouts/*.js` | 三种布局引擎：FlowLayout, RoundTableLayout, RadialLayout |
| `themes/*.css` | 所有主题新增群聊+拓扑颜色变量 |

---

### Task 1: 后端 — SessionContext 持久化 SubagentRegistry

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: 修改 SessionContext**

在 `SessionContext` 中增加 `SubagentRegistry` 字段：

```java
private static class SessionContext {
    AgentLoop agentLoop;
    boolean isRunning;
    String sessionId;
    SubagentRegistry subagentRegistry; // 新增
}
```

- [ ] **Step 2: 在 startChat() 构建完成后保存 registry**

在 `SubagentManagementCapability` 创建后、构建 `agentLoop` 之前，将 `subagentRegistry` 存入 `ctx`：

```java
SubagentManagementCapability subagentCap =
        new SubagentManagementCapability(factory, subagentRegistry, subagentListener);
ctx.subagentRegistry = subagentRegistry; // 新增
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java
git commit -m "feat(graph): persist SubagentRegistry in SessionContext"
```

---

### Task 2: 后端 — WebSocket 支持 targetAgent

**Files:**
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java`
- Modify: `coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java`

- [ ] **Step 1: Handler 解析 targetAgent**

```java
} else if ("chat".equals(action)) {
    String userMessage = jsonNode.path("message").asText("");
    String targetAgent = jsonNode.path("targetAgent").asText("");
    if (!userMessage.isEmpty()) {
        if (targetAgent.isEmpty() || "main".equals(targetAgent)) {
            agentService.startChat(userMessage, session);
        } else {
            agentService.sendToSubagent(targetAgent, userMessage, session);
        }
    }
}
```

- [ ] **Step 2: AgentService 新增 sendToSubagent**

在 `AgentService` 中新增方法：

```java
public void sendToSubagent(String targetAgent, String message, WebSocketSession session) {
    SessionContext ctx = sessions.get(session.getId());
    if (ctx == null || ctx.subagentRegistry == null) {
        sendError(session, "Session not initialized or no subagents available");
        return;
    }
    SubagentInstance inst = ctx.subagentRegistry.get(targetAgent);
    if (inst == null || inst.agentLoop == null) {
        sendError(session, "Subagent '" + targetAgent + "' not found");
        return;
    }
    inst.agentLoop.injectUserMessage(message);
    sendSystem(session, "Message sent to " + targetAgent);
}
```

注意：`SubagentInstance` 当前可能没有 `agentLoop` 字段。需要先检查 `SubagentInstance` 的结构。如果它没有 `agentLoop`，需要通过其他方式获取。

**检查 SubagentInstance 现有字段：**

```java
public class SubagentInstance {
    public final String name;
    public final String description;
    public final AgentLoop agentLoop; // 可能有
    // ...
}
```

如果没有 `agentLoop`，需要修改为持有 `AgentLoop` 引用。

- [ ] **Step 3: 编译测试**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/java/com/coloop/agent/server/websocket/AgentWebSocketHandler.java
git add coloop-agent-server/src/main/java/com/coloop/agent/server/service/AgentService.java
git commit -m "feat(graph): support targetAgent for direct subagent messaging"
```

---

### Task 3: 前端 — index.html 结构改造

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/index.html`

- [ ] **Step 1: 顶部导航栏增加模式切换**

将现有主题选择器旁边增加模式切换：

```html
<div class="header-right">
    <div class="mode-switcher">
        <button class="mode-btn active" data-mode="single">单聊</button>
        <button class="mode-btn" data-mode="combined">复合</button>
    </div>
    <!-- 现有主题切换器 -->
</div>
```

- [ ] **Step 2: 主内容区改为双模式容器**

```html
<div class="main-content" id="main-content">
    <!-- 单聊模式（现有）-->
    <div class="single-mode" id="single-mode">
        <!-- 现有 header + context-bar + chat-container + input-area -->
    </div>

    <!-- 复合模式（新增）-->
    <div class="combined-mode" id="combined-mode" style="display:none">
        <div class="topology-panel" id="topology-panel">
            <div class="topology-header">
                <select id="topology-layout-select">
                    <option value="flow">业务流程</option>
                    <option value="roundtable">圆桌</option>
                    <option value="radial">放射</option>
                </select>
                <span class="topology-toggle" id="topology-toggle">◀</span>
            </div>
            <div class="topology-canvas" id="topology-canvas">
                <svg id="topology-svg" class="topology-svg"></svg>
                <div id="topology-nodes"></div>
            </div>
        </div>
        <div class="group-chat-panel" id="group-chat-panel">
            <div class="group-chat-members" id="group-chat-members"></div>
            <div class="group-chat-messages" id="group-chat-messages"></div>
            <div class="group-chat-input">
                <label>发给:</label>
                <select id="group-target-select"><option>main</option></select>
                <input type="text" id="group-message-input" placeholder="输入消息...">
                <button id="group-send-btn">发送</button>
            </div>
        </div>
    </div>
</div>
```

现有 `.main-content` 内的 `header`、`context-bar`、`chat-container`、`input-area` 需要包裹进 `single-mode` div 中。

- [ ] **Step 3: 引入新 JS 文件**

在 `index.html` 底部增加：

```html
<script src="graph-state.js?v=1"></script>
<script src="group-chat.js?v=1"></script>
<script src="topology.js?v=1"></script>
```

放在 `chat.js` 之后。

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/index.html
git commit -m "feat(graph): add combined mode HTML structure with topology and group chat panels"
```

---

### Task 4: 前端 — graph-state.js 统一状态层

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/graph-state.js`

- [ ] **Step 1: 编写 GraphState**

```javascript
(function() {
    window.GraphState = {
        agents: new Map(),
        groupMessages: [],
        currentMode: 'single',
        topologyLayout: 'flow',
        topologyCollapsed: false,

        updateFromMessage(msg) {
            const agent = msg.agentName || 'main';
            const type = msg.type;
            const payload = msg.payload || {};

            let state = this.agents.get(agent);
            if (!state && type !== 'subagent_created') {
                state = {
                    name: agent,
                    status: 'idle',
                    currentActivity: '',
                    lastMessage: '',
                    lastMessageTime: 0,
                    createdBy: 'main',
                    subagents: [],
                    position: { x: 0, y: 0 }
                };
                this.agents.set(agent, state);
            }

            switch (type) {
                case 'subagent_created':
                    this.agents.set(payload.name, {
                        name: payload.name,
                        status: 'idle',
                        currentActivity: '',
                        lastMessage: '',
                        lastMessageTime: 0,
                        createdBy: agent,
                        subagents: [],
                        position: { x: 0, y: 0 }
                    });
                    const parent = this.agents.get(agent);
                    if (parent) parent.subagents.push(payload.name);
                    break;

                case 'subagent_cleared':
                    const cleared = this.agents.get(payload.name);
                    if (cleared) {
                        cleared.status = 'removed';
                        setTimeout(() => this.agents.delete(payload.name), 3000);
                    }
                    const p = this.agents.get(agent);
                    if (p) {
                        p.subagents = p.subagents.filter(n => n !== payload.name);
                    }
                    break;

                case 'loop_start':
                    if (state) { state.status = 'working'; state.currentActivity = 'loop_start'; }
                    break;

                case 'thinking':
                    if (state) { state.status = 'working'; state.currentActivity = 'thinking'; }
                    break;

                case 'tool_call':
                    if (state) { state.status = 'working'; state.currentActivity = 'tool_call:' + payload.name; }
                    break;

                case 'stream_chunk':
                    if (state) { state.status = 'working'; state.currentActivity = 'stream'; }
                    break;

                case 'assistant':
                    if (state) {
                        state.status = 'answering';
                        state.currentActivity = '';
                        const content = payload.content || '';
                        state.lastMessage = content.substring(0, 80) + (content.length > 80 ? '...' : '');
                        state.lastMessageTime = Date.now();
                        setTimeout(() => { if (state.status === 'answering') state.status = 'idle'; }, 5000);
                    }
                    this.addGroupMessage({
                        type: 'assistant',
                        agent: agent,
                        content: payload.content || ''
                    });
                    break;

                case 'user':
                    this.addGroupMessage({
                        type: 'user',
                        agent: 'user',
                        targetAgent: agent,
                        content: payload.content || ''
                    });
                    break;

                case 'system':
                    this.addGroupMessage({
                        type: 'system',
                        agent: 'system',
                        content: payload.message || ''
                    });
                    break;
            }

            this.notifyListeners();
        },

        addGroupMessage(msg) {
            const last = this.groupMessages[this.groupMessages.length - 1];
            if (last && last.type === msg.type && last.agent === msg.agent) {
                last.content += '\n' + msg.content;
                last.id = Date.now() + '-' + msg.agent + '-' + this.groupMessages.length;
            } else {
                this.groupMessages.push({
                    id: Date.now() + '-' + (msg.agent || 'sys') + '-' + this.groupMessages.length,
                    ...msg,
                    timestamp: Date.now()
                });
            }
        },

        listeners: [],
        onChange(fn) { this.listeners.push(fn); },
        notifyListeners() {
            this.listeners.forEach(fn => fn(this));
        },

        getAgentList() {
            return Array.from(this.agents.values()).filter(a => a.status !== 'removed');
        },

        setTopologyLayout(layout) {
            this.topologyLayout = layout;
            this.notifyListeners();
        },

        setTopologyCollapsed(collapsed) {
            this.topologyCollapsed = collapsed;
            this.notifyListeners();
        }
    };
})();
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/graph-state.js
git commit -m "feat(graph): add unified GraphState layer"
```

---

### Task 5: 前端 — chat.js 集成 GraphState 和模式切换

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: handleMessage 中同步到 GraphState**

在 `handleMessage` 的 `switch` 之后、底部之前添加：

```javascript
if (window.GraphState) {
    window.GraphState.updateFromMessage(msg);
}
```

- [ ] **Step 2: 模式切换逻辑**

在 `chat.js` 底部连接代码之后添加：

```javascript
// Mode switching
const modeBtns = document.querySelectorAll('.mode-btn');
const singleMode = document.getElementById('single-mode');
const combinedMode = document.getElementById('combined-mode');

modeBtns.forEach(function(btn) {
    btn.addEventListener('click', function() {
        var mode = btn.dataset.mode;
        modeBtns.forEach(function(b) { b.classList.remove('active'); });
        btn.classList.add('active');

        if (mode === 'single') {
            singleMode.style.display = '';
            combinedMode.style.display = 'none';
            window.GraphState.currentMode = 'single';
        } else {
            singleMode.style.display = 'none';
            combinedMode.style.display = '';
            window.GraphState.currentMode = 'combined';
        }
    });
});
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(graph): integrate GraphState and mode switching into chat.js"
```

---

### Task 6: 前端 — group-chat.js 群聊 UI

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/group-chat.js`
- Create: `coloop-agent-server/src/main/resources/static/group-chat.css`

- [ ] **Step 1: group-chat.js**

```javascript
(function() {
    const messagesEl = document.getElementById('group-chat-messages');
    const membersEl = document.getElementById('group-chat-members');
    const targetSelect = document.getElementById('group-target-select');
    const inputEl = document.getElementById('group-message-input');
    const sendBtn = document.getElementById('group-send-btn');

    if (!messagesEl) return;

    function render() {
        const gs = window.GraphState;
        if (!gs) return;

        // Render messages
        if (gs.groupMessages.length > 0) {
            renderMessages(gs.groupMessages);
        }

        // Render members
        renderMembers(gs.getAgentList());

        // Update target select
        updateTargetSelect(gs.getAgentList());
    }

    function renderMessages(messages) {
        const html = messages.map(function(m) {
            if (m.type === 'system') {
                return '<div class="group-system-msg">' + escapeHtml(m.content) + '</div>';
            }
            if (m.type === 'user') {
                var label = m.targetAgent && m.targetAgent !== 'main'
                    ? '我 → ' + m.targetAgent
                    : '我';
                return '<div class="group-msg group-msg-self">' +
                    '<div class="group-msg-label">' + escapeHtml(label) + '</div>' +
                    '<div class="group-msg-bubble">' + escapeHtml(m.content) + '</div>' +
                    '</div>';
            }
            // assistant
            return '<div class="group-msg group-msg-other">' +
                '<div class="group-msg-avatar">🤖</div>' +
                '<div class="group-msg-content">' +
                '<div class="group-msg-name">' + escapeHtml(m.agent) + '</div>' +
                '<div class="group-msg-bubble">' + escapeHtml(m.content) + '</div>' +
                '</div>' +
                '</div>';
        }).join('');

        if (messagesEl.innerHTML !== html) {
            messagesEl.innerHTML = html;
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }
    }

    function renderMembers(agents) {
        var html = '<div class="group-members-header">群成员 (' + (agents.length + 1) + ')</div>';
        // main always first
        html += '<div class="group-member"><span class="group-member-avatar">⭐</span><span class="group-member-name">main</span><span class="group-member-role">群主</span></div>';
        agents.forEach(function(a) {
            if (a.name === 'main') return;
            var statusText = a.status === 'working' ? '工作中' : '空闲';
            html += '<div class="group-member">' +
                '<span class="group-member-avatar">🤖</span>' +
                '<span class="group-member-name">' + escapeHtml(a.name) + '</span>' +
                '<span class="group-member-status">' + statusText + '</span>' +
                '</div>';
        });
        membersEl.innerHTML = html;
    }

    function updateTargetSelect(agents) {
        var currentValue = targetSelect.value;
        var options = ['<option>main</option>'];
        agents.forEach(function(a) {
            if (a.name !== 'main') {
                options.push('<option value="' + a.name + '">' + a.name + '</option>');
            }
        });
        targetSelect.innerHTML = options.join('');
        if (currentValue) targetSelect.value = currentValue;
    }

    function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function sendGroupMessage() {
        var text = inputEl.value.trim();
        if (!text || !window.ws || window.ws.readyState !== WebSocket.OPEN) return;
        var target = targetSelect.value || 'main';
        window.ws.send(JSON.stringify({ action: 'chat', message: text, targetAgent: target }));
        inputEl.value = '';
    }

    sendBtn.addEventListener('click', sendGroupMessage);
    inputEl.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendGroupMessage();
        }
    });

    window.GraphState.onChange(render);
    render();
})();
```

- [ ] **Step 2: group-chat.css**

```css
/* Combined Mode Layout */
.combined-mode {
    display: flex;
    height: 100%;
    overflow: hidden;
}

/* Topology Panel */
.topology-panel {
    width: 40%;
    min-width: 300px;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--sidebar-border, #e5e7eb);
    background: var(--topo-bg, #f5f5f5);
    transition: width 0.3s ease;
    position: relative;
}
.topology-panel.collapsed {
    width: 40px;
    min-width: 40px;
}
.topology-panel.collapsed .topology-header select,
.topology-panel.collapsed .topology-canvas {
    display: none;
}
.topology-header {
    padding: 8px 12px;
    border-bottom: 1px solid var(--sidebar-border, #e5e7eb);
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--sidebar-bg, #f8f7fa);
}
.topology-header select {
    padding: 4px 8px;
    border-radius: 4px;
    border: 1px solid var(--sidebar-border, #e5e7eb);
    font-size: 13px;
}
.topology-toggle {
    cursor: pointer;
    font-size: 14px;
    padding: 4px;
    border-radius: 4px;
    user-select: none;
}
.topology-toggle:hover {
    background: var(--sidebar-toggle-hover-bg, #e5e7eb);
}
.topology-canvas {
    flex: 1;
    position: relative;
    overflow: hidden;
}
.topology-svg {
    position: absolute;
    top: 0; left: 0;
    width: 100%; height: 100%;
    pointer-events: none;
    z-index: 1;
}

/* Group Chat Panel */
.group-chat-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    background: var(--group-bg, #fff);
}
.group-chat-members {
    width: 180px;
    border-left: 1px solid var(--sidebar-border, #e5e7eb);
    background: var(--group-sidebar-bg, #f8f7fa);
    padding: 12px;
    overflow-y: auto;
    position: absolute;
    right: 0;
    top: 0;
    bottom: 56px;
}
.group-members-header {
    font-size: 12px;
    font-weight: 600;
    color: var(--sidebar-section-header-color, #666);
    margin-bottom: 12px;
    text-transform: uppercase;
}
.group-member {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    font-size: 13px;
}
.group-member-avatar {
    font-size: 16px;
}
.group-member-name {
    flex: 1;
}
.group-member-role {
    font-size: 11px;
    color: var(--sidebar-section-header-color, #999);
    background: var(--sidebar-icon-bg, #eee);
    padding: 2px 6px;
    border-radius: 4px;
}
.group-member-status {
    font-size: 11px;
    color: var(--sidebar-section-header-color, #999);
}

.group-chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    padding-right: 196px; /* space for members sidebar */
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.group-msg {
    display: flex;
    gap: 10px;
    max-width: 80%;
}
.group-msg-self {
    align-self: flex-end;
    flex-direction: column;
    align-items: flex-end;
    margin-left: auto;
}
.group-msg-other {
    align-self: flex-start;
}
.group-msg-avatar {
    font-size: 28px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
.group-msg-name {
    font-size: 12px;
    color: var(--sidebar-section-header-color, #666);
    margin-bottom: 2px;
}
.group-msg-label {
    font-size: 11px;
    color: var(--sidebar-section-header-color, #999);
    margin-bottom: 2px;
}
.group-msg-bubble {
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.5;
    word-break: break-word;
}
.group-msg-self .group-msg-bubble {
    background: var(--group-bubble-self-bg, #2563eb);
    color: var(--group-bubble-self-color, #fff);
    border-radius: 12px 12px 4px 12px;
}
.group-msg-other .group-msg-bubble {
    background: var(--group-bubble-other-bg, #f3f4f6);
    color: var(--group-bubble-other-color, #111);
    border-radius: 12px 12px 12px 4px;
}
.group-system-msg {
    align-self: center;
    font-size: 12px;
    color: var(--group-system-color, #9ca3af);
    padding: 4px 12px;
}

.group-chat-input {
    display: flex;
    gap: 8px;
    padding: 10px 16px;
    border-top: 1px solid var(--sidebar-border, #e5e7eb);
    background: var(--sidebar-bg, #f8f7fa);
    align-items: center;
}
.group-chat-input label {
    font-size: 13px;
    color: var(--sidebar-item-color, #555);
    white-space: nowrap;
}
.group-chat-input select {
    padding: 6px 10px;
    border-radius: 6px;
    border: 1px solid var(--sidebar-border, #e5e7eb);
    font-size: 13px;
    background: var(--group-bg, #fff);
}
.group-chat-input input {
    flex: 1;
    padding: 8px 12px;
    border-radius: 6px;
    border: 1px solid var(--sidebar-border, #e5e7eb);
    font-size: 14px;
    outline: none;
}
.group-chat-input input:focus {
    border-color: var(--sidebar-item-active-border, #2563eb);
}
.group-chat-input button {
    padding: 8px 18px;
    background: var(--sidebar-item-active-border, #2563eb);
    color: #fff;
    border: none;
    border-radius: 6px;
    font-size: 14px;
    cursor: pointer;
}

/* Mode Switcher */
.mode-switcher {
    display: flex;
    gap: 2px;
    background: var(--sidebar-bg, #f0f0f0);
    border-radius: 6px;
    padding: 2px;
}
.mode-btn {
    padding: 4px 12px;
    border: none;
    background: transparent;
    border-radius: 4px;
    font-size: 13px;
    cursor: pointer;
    color: var(--sidebar-item-color, #555);
}
.mode-btn.active {
    background: var(--group-bg, #fff);
    color: var(--sidebar-item-active-color, #111);
    font-weight: 500;
    box-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

/* Agent Node in Topology */
.agent-node {
    position: absolute;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    transition: left 0.4s ease, top 0.4s ease;
    z-index: 2;
}
.agent-node-avatar {
    width: 64px;
    height: 64px;
    font-size: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--sidebar-icon-bg, #e5e7eb);
    border-radius: 12px;
    border: 2px solid var(--topo-node-border, #e5e7eb);
    image-rendering: pixelated;
    transition: all 0.3s ease;
}
.agent-node-main .agent-node-avatar {
    border-color: var(--sidebar-item-active-border, #2563eb);
    box-shadow: 0 0 0 3px rgba(37,99,235,0.15);
}
.agent-node-working .agent-node-avatar {
    animation: node-float 2s ease-in-out infinite;
}
@keyframes node-float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-3px); }
}
.agent-node-idle .agent-node-avatar {
    opacity: 0.6;
    filter: grayscale(0.5);
}
.agent-node-status {
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 10px;
    background: var(--sidebar-bg, #f0f0f0);
    color: var(--sidebar-item-active-border, #2563eb);
    font-weight: 500;
    white-space: nowrap;
}
.agent-node-bubble {
    max-width: 200px;
    padding: 8px 12px;
    background: var(--topo-bubble-bg, #fff);
    border: 1px solid var(--topo-node-border, #e5e7eb);
    border-radius: 10px;
    font-size: 12px;
    line-height: 1.4;
    word-break: break-word;
    position: relative;
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.agent-node-bubble::before {
    content: '';
    position: absolute;
    bottom: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 6px solid transparent;
    border-bottom-color: var(--topo-node-border, #e5e7eb);
}
.agent-node-bubble::after {
    content: '';
    position: absolute;
    bottom: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 5px solid transparent;
    border-bottom-color: var(--topo-bubble-bg, #fff);
    margin-bottom: -1px;
}
.agent-node-actions {
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s ease;
}
.agent-node:hover .agent-node-actions {
    opacity: 1;
}
.agent-node-actions button {
    font-size: 11px;
    padding: 3px 8px;
    border: 1px solid var(--sidebar-border, #e5e7eb);
    border-radius: 4px;
    background: var(--group-bg, #fff);
    cursor: pointer;
    color: var(--sidebar-item-color, #555);
}
.agent-node-actions button:hover {
    background: var(--sidebar-item-hover-bg, #e5e7eb);
}

/* Topology Line Animation */
.topo-line {
    fill: none;
    stroke: var(--topo-line, #ccc);
    stroke-width: 2;
}
.topo-line.active {
    stroke: var(--topo-line-active, #2563eb);
    stroke-dasharray: 6 4;
    animation: line-flow 1s linear infinite;
}
@keyframes line-flow {
    to { stroke-dashoffset: -20; }
}
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/group-chat.js
git add coloop-agent-server/src/main/resources/static/group-chat.css
git commit -m "feat(graph): add group chat UI with members list and target select"
```

---

### Task 7: 前端 — topology.js 拓扑画布

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/topology.js`

- [ ] **Step 1: topology.js**

```javascript
(function() {
    const canvas = document.getElementById('topology-canvas');
    const nodesContainer = document.getElementById('topology-nodes');
    const svg = document.getElementById('topology-svg');
    const layoutSelect = document.getElementById('topology-layout-select');
    const toggleBtn = document.getElementById('topology-toggle');
    const panel = document.getElementById('topology-panel');

    if (!canvas) return;

    // Layouts
    const layouts = {
        flow: {
            compute(agents, w, h) {
                const positions = {};
                const levels = {};
                const main = agents.find(function(a) { return a.name === 'main'; });
                if (main) {
                    levels[0] = [main];
                    positions[main.name] = { x: 80, y: h / 2 };
                }
                var depth = 1;
                var prev = main ? [main] : [];
                while (prev.length > 0) {
                    var next = [];
                    prev.forEach(function(p) {
                        p.subagents.forEach(function(name) {
                            var a = agents.find(function(x) { return x.name === name; });
                            if (a) next.push(a);
                        });
                    });
                    if (next.length === 0) break;
                    var spacing = Math.min(120, h / (next.length + 1));
                    next.forEach(function(a, i) {
                        positions[a.name] = { x: 80 + depth * 200, y: (h - (next.length - 1) * spacing) / 2 + i * spacing };
                    });
                    levels[depth] = next;
                    prev = next;
                    depth++;
                }
                return positions;
            }
        },
        roundtable: {
            compute(agents, w, h) {
                const positions = {};
                const subs = agents.filter(function(a) { return a.name !== 'main'; });
                const cx = w / 2, cy = h / 2;
                const radius = Math.min(w, h) * 0.35;

                if (agents.find(function(a) { return a.name === 'main'; })) {
                    positions['main'] = { x: w * 0.5, y: 60 };
                }
                subs.forEach(function(a, i) {
                    var angle = (i / subs.length) * 2 * Math.PI - Math.PI / 2;
                    positions[a.name] = {
                        x: cx + radius * Math.cos(angle),
                        y: cy + radius * Math.sin(angle)
                    };
                });
                return positions;
            }
        },
        radial: {
            compute(agents, w, h) {
                const positions = {};
                const subs = agents.filter(function(a) { return a.name !== 'main'; });
                const cx = w / 2, cy = h / 2;
                const radius = Math.min(w, h) * 0.38;

                if (agents.find(function(a) { return a.name === 'main'; })) {
                    positions['main'] = { x: cx, y: cy };
                }
                subs.forEach(function(a, i) {
                    var angle = (i / subs.length) * 2 * Math.PI - Math.PI / 2;
                    positions[a.name] = {
                        x: cx + radius * Math.cos(angle),
                        y: cy + radius * Math.sin(angle)
                    };
                });
                return positions;
            }
        }
    };

    function render() {
        const gs = window.GraphState;
        if (!gs) return;
        const agents = gs.getAgentList();
        if (agents.length === 0) return;

        const layout = layouts[gs.topologyLayout] || layouts.flow;
        const positions = layout.compute(agents, canvas.clientWidth, canvas.clientHeight);

        // Update agent positions in state
        agents.forEach(function(a) {
            if (positions[a.name]) {
                a.position = positions[a.name];
            }
        });

        renderNodes(agents);
        renderConnections(agents, positions);
    }

    function renderNodes(agents) {
        var html = '';
        agents.forEach(function(a) {
            var isMain = a.name === 'main';
            var statusClass = a.status === 'working' ? 'agent-node-working' : (a.status === 'idle' ? 'agent-node-idle' : '');
            var statusText = '';
            if (a.status === 'working') {
                if (a.currentActivity === 'thinking') statusText = '💭 思考中…';
                else if (a.currentActivity && a.currentActivity.startsWith('tool_call:')) statusText = '🔧 ' + a.currentActivity.split(':')[1];
                else if (a.currentActivity === 'stream') statusText = '✍️ 输出中…';
                else statusText = '🚀 运行中…';
            }
            var bubbleHtml = '';
            if (a.status === 'answering' && a.lastMessage) {
                bubbleHtml = '<div class="agent-node-bubble">' + escapeHtml(a.lastMessage) + '</div>';
            }

            html += '<div class="agent-node ' + (isMain ? 'agent-node-main ' : '') + statusClass + '"' +
                ' style="left:' + a.position.x + 'px;top:' + a.position.y + 'px;transform:translate(-50%,-50%)"' +
                ' data-agent="' + a.name + '"'> +
                '<div class="agent-node-avatar">' + (isMain ? '⭐' : '🤖') + '</div>' +
                (statusText ? '<div class="agent-node-status">' + statusText + '</div>' : '') +
                bubbleHtml +
                '<div class="agent-node-actions">' +
                '<button onclick="jumpToAgent(\'' + a.name + '\')">跳转</button>' +
                '<button onclick="communicateWithAgent(\'' + a.name + '\')">沟通</button>' +
                '</div>' +
                '</div>';
        });
        nodesContainer.innerHTML = html;
    }

    function renderConnections(agents, positions) {
        var paths = '';
        agents.forEach(function(a) {
            if (!a.createdBy || !positions[a.createdBy]) return;
            var from = positions[a.createdBy];
            var to = positions[a.name];
            if (!from || !to) return;
            var isActive = a.status === 'working';
            var d = 'M' + from.x + ' ' + from.y + ' L' + to.x + ' ' + to.y;
            paths += '<path d="' + d + '" class="topo-line' + (isActive ? ' active' : '') + '" />';
        });
        svg.innerHTML = paths;
    }

    function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    window.jumpToAgent = function(name) {
        // Switch to single mode and select agent
        var singleBtn = document.querySelector('.mode-btn[data-mode="single"]');
        if (singleBtn) singleBtn.click();
        // Then switch to that agent in sidebar
        var items = document.querySelectorAll('.agent-item');
        items.forEach(function(item) {
            if (item.dataset.agent === name) item.click();
        });
    };

    window.communicateWithAgent = function(name) {
        // Set target in group chat and focus input
        var targetSelect = document.getElementById('group-target-select');
        var input = document.getElementById('group-message-input');
        if (targetSelect) targetSelect.value = name;
        if (input) input.focus();
    };

    layoutSelect.addEventListener('change', function() {
        window.GraphState.setTopologyLayout(layoutSelect.value);
    });

    toggleBtn.addEventListener('click', function() {
        var collapsed = panel.classList.toggle('collapsed');
        toggleBtn.textContent = collapsed ? '▶' : '◀';
        window.GraphState.setTopologyCollapsed(collapsed);
    });

    window.GraphState.onChange(render);

    // Initial render after a short delay for DOM sizing
    setTimeout(render, 100);
})();
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/topology.js
git commit -m "feat(graph): add topology canvas with Flow/RoundTable/Radial layouts"
```

---

### Task 8: 前端 — 单聊模式增加「发给」下拉框

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/index.html`
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

- [ ] **Step 1: index.html 中给单聊输入区加下拉框**

在单聊模式的 `.input-area` 中，在 `textarea` 前面增加发给选择：

```html
<footer class="input-area" style="position:relative;">
    <div id="command-suggestions" class="command-suggestions"></div>
    <label style="font-size:12px;color:#666;align-self:center;white-space:nowrap;">发给:</label>
    <select id="single-target-select" style="padding:6px 10px;border-radius:6px;border:1px solid #d1d5db;font-size:13px;background:#fff;align-self:center;">
        <option>main</option>
    </select>
    <textarea id="message-input" placeholder="输入消息..." rows="2"></textarea>
    <button id="send-btn" disabled>发送</button>
</footer>
```

- [ ] **Step 2: chat.js sendMessage 带 targetAgent**

```javascript
function sendMessage() {
    const text = messageInput.value.trim();
    if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;

    var targetSelect = document.getElementById('single-target-select');
    var target = targetSelect ? targetSelect.value : 'main';
    ws.send(JSON.stringify({ action: 'chat', message: text, targetAgent: target }));
    messageInput.value = '';
    hideSuggestions();
}
```

- [ ] **Step 3: chat.js 动态更新下拉框选项**

在 `addAgentToCurrentSession` 和 `removeAgentFromSidebar` 中同步更新下拉框：

```javascript
function addAgentToCurrentSession(name, meta) {
    // ... existing code ...
    // Update single-mode target select
    var singleSelect = document.getElementById('single-target-select');
    if (singleSelect && !singleSelect.querySelector('option[value="' + name + '"]')) {
        var opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        singleSelect.appendChild(opt);
    }
}

function removeAgentFromSidebar(name) {
    // ... existing code ...
    // Remove from single-mode target select
    var singleSelect = document.getElementById('single-target-select');
    if (singleSelect) {
        var opt = singleSelect.querySelector('option[value="' + name + '"]');
        if (opt) opt.remove();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/index.html
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(graph): add targetAgent select to single chat mode"
```

---

### Task 9: 前端 — 主题 CSS 变量

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/themes/*.css` (9 个文件)

- [ ] **Step 1: 每个主题文件新增变量**

在每个主题的 `body` 规则中，在现有 sidebar 变量之后添加：

```css
--group-bg: #fff;
--group-sidebar-bg: #f8f7fa;
--group-bubble-self-bg: #2563eb;
--group-bubble-self-color: #fff;
--group-bubble-other-bg: #f3f4f6;
--group-bubble-other-color: #111;
--group-system-color: #9ca3af;
--topo-bg: #f5f5f5;
--topo-node-border: #e5e7eb;
--topo-bubble-bg: #fff;
--topo-line: rgba(0,0,0,0.15);
--topo-line-active: var(--sidebar-item-active-border, #2563eb);
```

暗色主题（glass, cursor, discord, terminal）使用对应的暗色值：

```css
/* glass theme */
--group-bg: rgba(255,255,255,0.05);
--group-sidebar-bg: rgba(255,255,255,0.06);
--group-bubble-self-bg: rgba(255,255,255,0.2);
--group-bubble-self-color: #fff;
--group-bubble-other-bg: rgba(255,255,255,0.1);
--group-bubble-other-color: rgba(255,255,255,0.9);
--group-system-color: rgba(255,255,255,0.4);
--topo-bg: transparent;
--topo-node-border: rgba(255,255,255,0.15);
--topo-bubble-bg: rgba(255,255,255,0.08);
--topo-line: rgba(255,255,255,0.15);
--topo-line-active: rgba(255,255,255,0.5);
```

对每个主题文件执行一次 Edit 操作。这里列出每个文件需要添加的具体值（在各自 theme 的 body 规则末尾添加）：

**default.css:**
```css
    --group-bg: #fff;
    --group-sidebar-bg: #f8f7fa;
    --group-bubble-self-bg: #2563eb;
    --group-bubble-self-color: #fff;
    --group-bubble-other-bg: #f3f4f6;
    --group-bubble-other-color: #111827;
    --group-system-color: #9ca3af;
    --topo-bg: #f5f5f5;
    --topo-node-border: #e5e7eb;
    --topo-bubble-bg: #fff;
    --topo-line: rgba(0,0,0,0.15);
    --topo-line-active: #2563eb;
```

**claude.css, chatgpt.css, linear.css, telegram.css:** 与 default 类似，使用各自的主色调替换 `--group-bubble-self-bg` 和 `--topo-line-active`。

**cursor.css, discord.css, terminal.css:** 使用暗色值，类似 glass。

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/themes/
git commit -m "feat(graph): add group chat and topology CSS variables to all themes"
```

---

### Task 10: 编译测试与最终提交

- [ ] **Step 1: 编译后端**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 检查所有新增/修改文件**

```bash
git status
```

- [ ] **Step 3: 最终提交（如果有多余未提交文件）**

```bash
git add -A
git commit -m "feat(graph): complete agent graph view with topology + group chat"
```

---

## Self-Review

**1. Spec coverage:**
- ✅ 复合模式左右分栏 — Task 3
- ✅ 拓扑三种布局 — Task 7
- ✅ 拓扑折叠 — Task 7
- ✅ 群聊消息过滤 — Task 4/6
- ✅ 群成员列表 — Task 6
- ✅ 发给下拉框 — Task 6/8
- ✅ 直接向子 agent 发消息 — Task 2
- ✅ 主题适配 — Task 9
- ✅ 跳转到单聊 — Task 7

**2. Placeholder scan:** 无 TBD/TODO。

**3. Type consistency:**
- `targetAgent` 字段在前后端一致使用
- `GraphState` 接口在 `graph-state.js`, `group-chat.js`, `topology.js` 中一致

**Gap identified:** `SubagentInstance` 需要确认是否有 `agentLoop` 字段。如果缺少，需要在 Task 2 之前修改 `SubagentInstance` 并在创建时传入 `AgentLoop`。
