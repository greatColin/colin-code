# Agent Graph View 设计文档

> **目标：** 在现有 Web 页面中新增「复合模式」，左侧拓扑图展示 agent 调用关系与工作状态，右侧群聊展示所有 agent 的最终发言。与现有「单聊模式」形成双模式架构。适合狼人杀等多 agent 围观场景。

**架构：** 前端采用「统一状态层 + 双模式切换」架构。复合模式为左右分栏（左拓扑右群聊）。后端扩展 WebSocket 支持 `targetAgent` 字段。两种模式共享同一套 WebSocket 数据流。

**技术栈：** 纯前端 Vanilla JS + CSS，SVG 画线，后端 Spring Boot WebSocket。

---

## 一、双模式总览

| 模式 | 定位 | 展示内容 | 适合场景 |
|------|------|---------|---------|
| **单聊模式**（现有） | 深度对话 | 单个 agent 的完整消息流（thinking/tool_call/assistant） | 与某个 agent 深入协作 |
| **复合模式**（新增） | 围观+监控 | 左侧拓扑（agent 关系+状态）+ 右侧群聊（过滤后的消息） | 狼人杀、多 agent 讨论、监控工作流 |

页面顶部导航栏增加模式切换器：

```
[单聊 | 复合]
```

## 二、复合模式整体布局

```
┌─────────────────────────────────────────────────────────────┐
│ coloop-agent Web    [单聊 | 复合]              [布局▼]      │
├──────────────┬──────────────────────────────────────────────┤
│              │                                              │
│   拓扑画布    │              群聊消息区域                      │
│   (左)        │              (右)                            │
│              │                                              │
│  ┌───┐       │  ┌───┐ 我 → dev                              │
│  │main│───┬───┤  │我 │ 去把那个 bug 修了                     │
│  └───┘   │   │  └───┘                                       │
│          ▼   │                                              │
│       ┌───┐  │  ┌───┐ dev                                    │
│       │dev│  │  │🤖 │ 已修复，请验证                          │
│       └───┘  │  └───┘                                       │
│              │                                              │
│       ┌───┐  │  ┌───┐ test                                   │
│       │test│  │  │🤖 │ 验证通过                               │
│       └───┘  │  └───┘                                       │
│              │                                              │
│  [◀折叠]     │                                              │
├──────────────┴──────────────────────────────────────────────┤
│  发给: [main ▼]  ┌──────────────────────────┐ [发送]        │
│                  │ 输入消息...                │               │
│                  └──────────────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 左侧成员头像列表

- 纵向排列当前所有 agent（main + 所有子 agent）
- 每个条目：像素头像（48×48）+ agent 名称
- 点击头像：快速滚动到该 agent 在群聊中的最新消息
- 头像带状态指示：绿色圆点=工作中，灰色=空闲
- 新 agent 加入时从顶部滑入动画；agent 被清除时淡出

### 2.3 中间群聊消息区域

**消息过滤规则（只显示这些类型）：**
- `user` — 用户发送的消息（标记发给谁）
- `assistant` — agent 的最终回复（流式完成后一次性显示）
- `system` — 系统消息（可选，默认折叠显示为灰色小字）

**不显示的过程消息：** `thinking`、`tool_call`、`tool_result`、`loop_start`、`stream_chunk`

**消息气泡样式：**
- 用户消息：右对齐，浅色背景，上方小字标注 "发给 dev"
- Agent 消息：左对齐，头像在气泡左侧，agent 名称在气泡上方
- 连续同 agent 消息：合并显示（头像只出现一次，类似微信）
- 系统消息：居中，小号灰色文字

**用户消息标注规则：**
- 发给 main 或不带 targetAgent：显示 "我"
- 发给子 agent（如 dev）：显示 "我 → dev"

**点击消息气泡：** 不展开，只高亮。要查看完整内容或历史上下文，点击气泡旁的「查看上下文」小按钮 → 跳转到单聊模式并自动选中该 agent。

### 2.4 右侧群成员列表

- 顶部：群名称 "coloop-agent 群聊" + 在线人数
- 列表分两组：
  - 群主：main（⭐ 标记）
  - 成员：各子 agent，按名称排序
- 每个成员条目：像素头像（24×24）+ 名称 + 状态文字
- 状态文字："工作中…" / "空闲" / 最后回复时间
- 点击成员：跳转到单聊模式并选中该 agent

### 2.5 底部输入区

```
┌────────────────────────────────────────────────────────┐
│ 发给: [main ▼]  ┌────────────────────────┐  [发送按钮] │
│                 │  输入消息...             │            │
│                 └────────────────────────┘            │
└────────────────────────────────────────────────────────┘
```

- **「发给」下拉框**：选项为当前所有活跃 agent（main + 所有已注册子 agent）
- 默认选中 main
- 子 agent 创建/清除时，下拉框动态更新
- 发送的 WebSocket 消息带 `targetAgent` 字段
- Shift+Enter 换行，Enter 发送

### 2.6 群聊状态管理

`group-chat-state.js` 维护：
```javascript
{
  messages: [],        // 过滤后的消息列表（user + assistant）
  agents: new Set(),   // 当前群成员
  targetAgent: 'main'  // 当前输入框选中的目标
}
```

消息数据结构：
```javascript
{
  id: string,          // 唯一标识（时间戳+agent+序号）
  type: 'user' | 'assistant' | 'system',
  agent: string,       // 发送者（user 时为 'user'）
  targetAgent: string, // 接收者（仅 user 消息有）
  content: string,     // 纯文本内容
  timestamp: number,   // 时间戳
  isMerged: boolean    // 是否与前一条合并显示
}
```

## 三、拓扑模式（Topology）

拓扑模式展示 agent 调用关系和工作状态。支持三种子布局，可热切换。

### 3.1 整体布局

```
┌─────────────────────────────────────────────────────────────┐
│ coloop-agent Web    [单聊 | 群聊 | 拓扑]     [拓扑布局▼]    │
├──────────┬──────────────────────────────────────────────────┤
│          │                                                  │
│  折叠    │              拓扑画布区域                         │
│  按钮    │                                                  │
│  ▶       │     ┌───┐                                        │
│          │     │main│──────┬────────┐                       │
│          │     └───┘      │        │                       │
│          │                ▼        ▼                       │
│          │              ┌───┐   ┌───┐                       │
│          │              │dev│   │test│                       │
│          │              └───┘   └───┘                       │
│          │                                                  │
│          │                                                  │
└──────────┴──────────────────────────────────────────────────┘
```

- 左侧有折叠按钮（◀），点击后拓扑画布收起，露出右侧的群聊/单聊内容（即拓扑模式变成悬浮面板覆盖在其他模式上方）
- 画布背景：点阵网格
- 左上角浮动面板：布局切换器

### 3.2 三种子布局

统一布局引擎接口：
```javascript
class BaseLayout {
  computePositions(agentStates, canvasW, canvasH) // 返回 name→{x,y}
  computeConnections(agentStates) // 返回 [{from, to, path}]
}
```

**FlowLayout（业务流程）：**
- main 在左侧，子 agent 向右树状展开
- 深度 0: main，深度 1: main 的子 agent
- 连线：水平树状贝塞尔曲线

**RoundTableLayout（圆桌）：**
- 画布中心为圆桌区域
- 子 agent 均匀分布在圆周
- main 在圆外上方/右侧当"主持人"
- 连线：从 main 辐射到各子 agent

**RadialLayout（放射）：**
- main 在画布中心
- 子 agent 均匀分布在圆周
- 连线：中心到各点直线

### 3.3 AgentNode 组件

```
┌─────────────────────────────┐
│  ┌──────────┐               │
│  │ 像素头像  │  ← 64×64     │
│  │   🤖     │               │
│  └──────────┘               │
│       ┌──────────────┐      │
│       │ 🟡 思考中...  │      │ ← 工作中显示
│       └──────────────┘      │
│  ┌──────────────────────┐   │
│  │ 最终回复摘要（3行）   │   │ ← 回答后显示
│  │ 不可展开             │   │
│  └──────────────────────┘   │
│  [查看详情] [沟通] [跳转]    │
└─────────────────────────────┘
```

**状态区分：**

| 状态 | 头像 | 标签 | 冒泡框 | 连线 |
|------|------|------|--------|------|
| idle | 灰度半透明 | 隐藏 | 隐藏 | 实线灰色 |
| working | 正常+微浮动动画 | 显示药丸标签 | 隐藏 | 虚线流动动画 |
| answering | 正常 | 隐藏 | 显示摘要，5秒后渐隐 | 实线高亮 |

**药丸标签内容：**
- `thinking` → "💭 思考中…"
- `tool_call:xxx` → "🔧 xxx"
- `stream` → "✍️ 输出中…"

**按钮行为：**
- **查看详情**：切换到群聊模式并滚动到该 agent 的最新消息
- **沟通**：在节点下方弹出迷你输入框，发完即关闭。发送带 targetAgent 的 WS 消息
- **跳转**：切换到单聊模式并选中该 agent

**连线（SVG）：**
- 线宽 2px，颜色跟随主题
- working 状态：虚线 + CSS `stroke-dashoffset` 流动动画
- idle/answering：实线
- 箭头在末端

### 3.4 拓扑模式折叠

拓扑模式支持向右折叠：
- 展开态：拓扑画布占满主内容区
- 折叠态：拓扑画布收缩为左侧窄条（约 50px），只显示布局切换器和展开按钮
- 折叠后，下方露出群聊模式或单聊模式的内容（取决于上一次活跃的非拓扑模式）
- 用途：用户想一边看拓扑监控，一边在群聊里参与讨论

实现方式：拓扑画布 `position: fixed; left: 0; top: 0; bottom: 0;`，折叠时 `width: 50px`，内容区 `margin-left: 50px`。

## 四、后端扩展：直接向子 Agent 发消息

### 4.1 WebSocket 协议扩展

`chat` action 增加可选 `targetAgent` 字段：
```json
{"action": "chat", "message": "...", "targetAgent": "dev"}
```

- `targetAgent` 为空或 `"main"`：走现有 `startChat()` 逻辑
- `targetAgent` 为子 agent 名称：走新 `sendToSubagent()` 逻辑

### 4.2 AgentService.sendToSubagent()

```java
public void sendToSubagent(String targetAgent, String message, WebSocketSession session) {
    SessionContext ctx = sessions.get(session.getId());
    if (ctx == null || ctx.agentLoop == null) {
        sendError(session, "Session not initialized");
        return;
    }
    // 从主 agentLoop 的能力中取出 SubagentManagementCapability
    // 进而获取 SubagentRegistry
    // registry.get(targetAgent) → SubagentInstance → AgentLoop
    // agentLoop.injectUserMessage(message)
    sendSystem(session, "Message sent to " + targetAgent);
}
```

**关键问题：** 当前 `SubagentManagementCapability` 和 `SubagentRegistry` 是在 `AgentService.startChat()` 内部创建的局部变量，外部无法访问。

**解决方案：** 在 `SessionContext` 中保存 `SubagentRegistry` 引用：
```java
private static class SessionContext {
    AgentLoop agentLoop;
    boolean isRunning;
    String sessionId;
    SubagentRegistry subagentRegistry; // 新增
}
```

在 `startChat()` 构建完成后存入：
```java
ctx.subagentRegistry = subagentRegistry;
```

**边界：** 子 agent 不存在时返回 error。

### 4.3 现有单聊模式也受益

扩展完成后，单聊模式下点击子 agent 聊天窗口后发送消息，本质是 `targetAgent` 不为空的 chat。单聊模式**不需要修改前端**即可支持此能力（因为现有消息发送就是 `action: 'chat'`，只是之前没有 `targetAgent` 字段，默认发给 main）。

但为了让单聊模式下也能选择发给谁，需要在单聊模式的输入区也增加「发给」下拉框（当子 agent 存在时显示）。

## 五、统一状态层（GraphState）

`graph-state.js` 维护全局状态，三个模式共享：

```javascript
const graphState = {
  // 所有 agent 的状态
  agents: new Map(), // name -> AgentState

  // 群聊消息（仅存储过滤后的 user + assistant）
  groupMessages: [],

  // 当前活跃模式
  currentMode: 'single', // 'single' | 'group' | 'topology'

  // 拓扑当前布局
  topologyLayout: 'flow', // 'flow' | 'roundtable' | 'radial'

  // 拓扑是否折叠
  topologyCollapsed: false
};
```

AgentState 结构：
```javascript
{
  name: 'main',
  status: 'working' | 'answering' | 'idle',
  currentActivity: 'thinking' | 'tool_call:xxx' | 'stream' | 'loop_start',
  lastMessage: '',     // 最后 assistant 消息的摘要（前 80 字）
  lastMessageTime: 0,  // 最后消息时间戳
  createdBy: null,     // 父 agent
  subagents: [],       // 子 agent 列表
  position: { x: 0, y: 0 } // 拓扑坐标
}
```

状态更新来源：复用现有 `chat.js` 的 `handleMessage`，在更新单聊 UI 的同时同步到 GraphState。

映射规则：

| WS 消息 | GraphState 更新 |
|---------|----------------|
| `subagent_created` | 新增 agent，设置 createdBy |
| `subagent_cleared` | 标记 removed，3 秒后删除 |
| `loop_start` | status='working', currentActivity='loop_start' |
| `thinking` | currentActivity='thinking' |
| `tool_call` | currentActivity='tool_call:'+name |
| `stream_chunk` | currentActivity='stream' |
| `assistant` | status='answering', lastMessage=摘要, lastMessageTime=now |
| `user` | 记录到 groupMessages（带 targetAgent） |

## 六、前端文件结构

```
static/
├── index.html              ← 三模式容器 + 顶部切换器
├── chat.js                 ← 现有单聊逻辑 + GraphState 同步
├── group-chat.js           ← 群聊模式逻辑
├── group-chat.css          ← 群聊模式样式
├── topology/
│   ├── topology.js         ← 拓扑模式主控制器
│   ├── topology.css        ← 拓扑样式
│   ├── graph-state.js      ← 统一状态层
│   ├── graph-components.js ← AgentNode、迷你输入框
│   ├── connection-line.js  ← SVG 连线
│   └── layouts/
│       ├── base-layout.js
│       ├── flow-layout.js
│       ├── roundtable-layout.js
│       └── radial-layout.js
└── themes/
    └── *.css               ← 所有主题新增群聊+拓扑变量
```

## 七、主题适配

每个主题 CSS 新增变量：

```css
/* 群聊模式 */
--group-bg: #fff;
--group-sidebar-bg: #f8f7fa;
--group-member-online: #22c55e;
--group-member-idle: #9ca3af;
--group-bubble-self-bg: #2563eb;
--group-bubble-self-color: #fff;
--group-bubble-other-bg: #f3f4f6;
--group-bubble-other-color: #111;
--group-system-color: #9ca3af;

/* 拓扑模式 */
--topo-bg: #f5f5f5;
--topo-grid: rgba(0,0,0,0.05);
--topo-node-bg: #fff;
--topo-node-border: #e5e7eb;
--topo-bubble-bg: #fff;
--topo-line: rgba(0,0,0,0.15);
--topo-line-active: var(--sidebar-item-active-border);
```

暗色主题反色处理。

## 八、边界与异常

1. **子 agent 被清除时正在沟通**：发送时后端返回 error，前端显示 toast "Agent 'dev' 已不存在"。
2. **大量子 agent**：>10 个时，圆桌布局自动缩小头像尺寸；群聊成员列表支持滚动。
3. **页面刷新**：所有模式状态不持久化，刷新后重建（与现有行为一致）。
4. **移动端**：<768px 时隐藏右侧群成员列表，拓扑模式禁用（或强制单列）。
5. **拓扑+群聊同时显示**：拓扑折叠后，下方显示群聊模式。用户可在同一屏幕同时监控拓扑和参与群聊。

## 九、Scope 确认

本 spec 涵盖：
- ✅ 群聊模式（QQ 群聊风格，左中右三栏）
- ✅ 拓扑模式（Flow/RoundTable/Radial 三种布局，可折叠）
- ✅ 统一状态层 GraphState
- ✅ 后端 targetAgent 支持 + SubagentRegistry 持久化到 SessionContext
- ✅ 单聊/群聊/拓扑三模式切换
- ✅ 9 套主题适配
- ✅ 拓扑迷你沟通输入框
- ✅ 从拓扑/群聊跳转到单聊上下文

本 spec **不**涵盖（后续迭代）：
- ❌ 自定义像素精灵图（先用 emoji + CSS image-rendering: pixelated）
- ❌ 画布拖拽/缩放（拓扑固定布局）
- ❌ 历史记录图形回放
- ❌ 群聊消息支持 markdown（先纯文本）
- ❌ 文件/图片消息在群聊中的展示
