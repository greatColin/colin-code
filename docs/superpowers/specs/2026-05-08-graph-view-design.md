# Agent Graph View 设计文档

> **目标：** 在现有 Web 页面中新增「图形视图」模式，以流程图/场景化形式展示主 agent 和子 agent 的运行状态，支持多布局切换和直接向子 agent 发消息。

**架构：** 前端采用「统一状态层 + 可插拔布局引擎 + 共享 UI 组件」三层架构。后端仅扩展 WebSocket 协议支持 `targetAgent` 字段，其余数据流复用现有 Hook 推送机制。布局之间可热切换，不丢失状态。

**技术栈：** 纯前端 Vanilla JS + CSS，SVG 画线，后端 Spring Boot WebSocket。

---

## 一、整体页面结构

现有 `index.html` 顶部增加视图切换器：

```
┌──────────────────────────────────────────┐
│ coloop-agent Web    [聊天视图 | 图形视图] │ ← 新增切换器
├──────────────────────────────────────────┤
│                                          │
│   聊天视图 / 图形视图 区域（互斥显示）       │
│                                          │
└──────────────────────────────────────────┘
```

- `chat-view`：现有聊天界面，完全保留
- `graph-view`：新增图形画布，默认 `display: none`
- 切换时平滑过渡，agent 状态不重置

## 二、前端状态层（GraphState）

`graph-state.js` 维护一个全局的 `agentStates` Map，key 为 agent 名称：

```javascript
{
  name: 'main',
  status: 'working' | 'answering' | 'idle',  // working = loop 进行中
  currentActivity: 'thinking' | 'tool_call:read_file' | 'stream', // 具体状态
  currentActivityDetail: '', // 状态附带的简短描述
  lastMessage: '',           // 最终 assistant 消息
  lastMessageFull: '',       // 完整消息（展开用）
  subagents: ['dev', 'test'],// 该 agent 创建的子 agent
  createdBy: null,           // 父 agent（main 为 null）
  expanded: true,            // 冒泡框是否展开
  x: 0, y: 0,                // 画布坐标（由布局引擎写入）
  timestamp: 0               // 最后更新时间
}
```

状态更新来源：复用现有 `chat.js` 的 WebSocket 消息处理，所有 `handleMessage` 分支在更新当前 agent 聊天 UI 的同时，向 GraphState 同步一份。

## 三、布局引擎（可插拔）

每个布局实现统一接口：

```javascript
class BaseLayout {
  // 传入所有 agentStates，返回 name -> {x, y} 的坐标映射
  computePositions(agentStates, canvasWidth, canvasHeight) {}
  // 返回连线路径数组 [{from, to, path}]（可选，SVG 使用）
  computeConnections(agentStates) {}
}
```

### 3.1 业务流程布局（FlowLayout）

- 根 agent（main）在左侧
- 子 agent 向右展开，每层深度 +200px X，同一层按 Y 均匀分布
- 深度 0: main
- 深度 1: main 的子 agent
- 深度 2: 子 agent 的子 agent（如果存在）
- 连线：水平树状贝塞尔曲线

适用场景：日常工作流、主 agent 分派任务给子 agent。

### 3.2 圆桌布局（RoundTableLayout）

- 画布中心留一个圆形区域（圆桌）
- 子 agent 均匀分布在圆周上
- 主 agent 在圆外上方或右侧，以"主持人"身份存在
- 连线：圆心辐射状，或 agent 之间依次相连（环形）
- 可配置：主 agent 是否在圆内（默认圆外）

适用场景：多 agent 讨论、狼人杀、圆桌会议。

### 3.3 放射布局（RadialLayout）

- 主 agent 固定在画布中心
- 子 agent 均匀分布在圆周，半径自适应数量
- 连线：中心到各点的直线

适用场景：展示主 agent 并行调度多个子 agent。

## 四、UI 组件

### 4.1 AgentNode（Agent 节点）

每个 agent 在画布上是一个绝对定位的 `div`：

```
┌─────────────────────────────┐
│  ┌──────────┐               │
│  │ 像素头像  │  ← 64×64     │
│  │   🤖     │   CSS 像素化  │
│  └──────────┘               │
│       ┌──────────────┐      │
│       │ 🟡 思考中...  │      │ ← 工作中显示，小药丸标签
│       └──────────────┘      │
│  ┌──────────────────────┐   │
│  │ ╭────────────────╮   │   │
│  │ │ 最终回复内容...  │   │   │ ← 回答中显示，冒泡框
│  │ │ 仅显示前3行      │   │   │
│  │ ╰────────────────╯   │   │
│  └──────────────────────┘   │
│  [展开] [沟通] [跳转]        │
└─────────────────────────────┘
```

**状态区分：**

| 状态 | 头像 | 标签 | 冒泡框 |
|------|------|------|--------|
| idle（未开始/已结束很久） | 灰度/半透明 | 隐藏 | 隐藏，只显示头像 |
| working（loop 进行中） | 正常+微动画（上下浮动 2px） | 显示当前活动 | 隐藏 |
| answering（loop 刚结束） | 正常 | 隐藏 | 显示，5 秒后自动折叠为摘要态 |

**药丸标签内容映射：**
- `thinking` → "💭 思考中…"
- `tool_call:xxx` → "🔧 调用 xxx"
- `stream` → "✍️ 输出中…"
- `loop_start` → "🚀 开始运行"

**冒泡框：**
- CSS 实现：圆角矩形 + 底部小三角指向头像
- 默认显示 `lastMessage` 的前 3 行（约 80 字），超出加 "…"
- 点击「展开」后显示完整 `lastMessageFull`（带 markdown 渲染）
- 冒泡框最大宽度 280px，最大高度 200px，溢出可滚动

**像素头像实现（简单版）：**
```css
.pixel-avatar {
  width: 64px; height: 64px;
  image-rendering: pixelated;
  font-size: 48px;
  display: flex; align-items: center; justify-content: center;
  background: var(--sidebar-icon-bg);
  border-radius: 8px;
  border: 2px solid var(--sidebar-border);
}
```
主 agent 头像边框使用 `--sidebar-item-active-border` 高亮色，子 agent 用普通边框。

### 4.2 ConnectionLine（连线）

使用 SVG overlay 层覆盖在画布上方：
- 线宽 2px，颜色跟随主题（`--sidebar-border` 或半透明白/黑）
- 工作中：虚线 + CSS `stroke-dashoffset` 流动动画
- 回答中/idle：实线，无动画
- 箭头在末端：SVG marker

### 4.3 布局切换器

Graph View 左上角浮动面板：
```
┌──────────────┐
│ 布局: [▼]    │
│ Flow         │
│ Round Table  │
│ Radial       │
└──────────────┘
```

切换布局时：
1. 新布局引擎计算坐标
2. 所有 AgentNode 播放 CSS transition 移动到 newX/newY（300ms ease-out）
3. SVG 连线同步重绘
4. 状态完全保留

## 五、交互：直接向子 Agent 发消息

### 5.1 前端：沟通按钮

点击 AgentNode 的「沟通」按钮：
1. 在该 AgentNode 下方弹出一个迷你输入框（类似聊天气泡底部附着的输入条）
2. 输入内容后点击发送，WebSocket 发送：
   ```json
   {"action": "chat", "message": "...", "targetAgent": "dev"}
   ```
   - `targetAgent` 为子 agent 名称，不传时默认发给 main（兼容现有行为）

### 5.2 后端：AgentService 扩展

`AgentWebSocketHandler.handleTextMessage` 中解析 `targetAgent`：
- 如果为空或 `"main"`：走现有 `startChat` 逻辑
- 如果为子 agent 名称：调用新 `sendToSubagent(targetAgent, message, session)` 方法

`sendToSubagent` 实现：
1. 从 `SessionContext` 中获取该 WebSocket 会话的 `SubagentRegistry`
2. `registry.get(targetAgent)` 获取 `SubagentInstance`
3. 从 instance 中获取 `AgentLoop`
4. 调用 `agentLoop.injectUserMessage(message)` 注入用户消息
5. 推送 `system` 消息给用户："Message sent to [targetAgent]"

**边界：** 子 agent 可能已被清除，此时返回 error："Subagent '[name]' not found."

### 5.3 现有聊天视图也受益

扩展完成后，现有侧边栏点击子 agent 聊天窗口时，用户也可以直接在该窗口输入并发送——本质上就是 `targetAgent` 不为空的 chat 消息。因此现有聊天视图**不需要额外修改**即可支持此能力。

## 六、消息流图

```
用户输入 ──► WebSocket ──► AgentWebSocketHandler
                                │
                    targetAgent?  │
                    ├─ null/main ─┼─► AgentService.startChat()
                    │                              │
                    │                    ┌─────────┴──────────┐
                    │                    │                    │
                    │              main AgentLoop      SubagentRegistry
                    │                    │                    │
                    │                    │              SubagentInstance
                    │                    │                    │
                    │                    │              Subagent AgentLoop
                    │                    │                    │
                    │                    │         injectUserMessage()
                    │                    │
                    └─ subagent ─────────┘────► sendToSubagent()
                                                    │
                                              SubagentRegistry.get()
                                                    │
                                              AgentLoop.injectUserMessage()
```

## 七、数据流：WebSocket → GraphState → UI

现有 `chat.js` 的 `handleMessage` 已经接收所有 agent 事件。扩展方式：

```javascript
function handleMessage(msg) {
    // ... 现有逻辑不变 ...

    // 新增：同步到 GraphState
    if (window.graphState) {
        window.graphState.updateFromMessage(msg);
    }
}
```

`GraphState.updateFromMessage` 映射规则：

| WS 消息类型 | GraphState 字段更新 |
|-------------|---------------------|
| `subagent_created` | 新增 agentState，设置 `createdBy=msg.agentName` |
| `subagent_cleared` | 标记 agent 为 removed，延迟 3 秒后从 Map 删除（配合退场动画） |
| `loop_start` | `status='working'`, `currentActivity='loop_start'` |
| `thinking` | `currentActivity='thinking'` |
| `tool_call` | `currentActivity='tool_call:' + name` |
| `stream_chunk` | `currentActivity='stream'` |
| `assistant` | `status='answering'`, `lastMessage=content`, `lastMessageFull=content` |
| `user` | 如果 targetAgent 匹配，记录用户输入到该 agent |

## 八、文件结构

```
static/
├── index.html              ← 新增 graph-view 容器 + 视图切换器
├── chat.js                 ← 现有逻辑 + GraphState 同步调用
├── graph-view.js           ← 图形视图主控制器（渲染、切换布局、动画）
├── graph-state.js          ← 全局状态管理
├── graph-components.js     ← AgentNode、ConnectionLine、迷你输入框组件
├── layouts/
│   ├── base-layout.js      ← 接口定义 + 工具函数
│   ├── flow-layout.js      ← 业务流程布局
│   ├── roundtable-layout.js← 圆桌布局
│   └── radial-layout.js    ← 放射布局
├── themes/
│   └── *.css               ← 所有主题新增 graph-view 相关变量
```

## 九、主题适配

每个主题 CSS 文件新增变量：

```css
--graph-bg: #f0f0f0;
--graph-grid: rgba(0,0,0,0.05);
--graph-node-bg: #fff;
--graph-node-border: #e5e7eb;
--graph-bubble-bg: #fff;
--graph-bubble-border: #e5e7eb;
--graph-line: rgba(0,0,0,0.2);
--graph-line-active: var(--sidebar-item-active-border);
```

暗色主题（glass、cursor、discord、terminal）反色处理。

## 十、边界与异常

1. **子 agent 被清除时正在沟通**：用户点击「沟通」弹出输入框后，子 agent 被清除了——发送时后端返回 error，前端显示 toast。
2. **大量子 agent**：>10 个子 agent 时，圆桌/放射布局自动缩小头像、降低冒泡框默认展开比例。
3. **页面刷新**：图形视图状态不持久化（与现有聊天视图一致，刷新后重建）。
4. **移动端**：图形视图在小屏幕（<768px）下回退到聊天视图，或强制单列垂直布局。

## 十一、Scope 确认

本 spec 涵盖：
- ✅ 三种布局（Flow、Round Table、Radial）
- ✅ 统一 GraphState 状态层
- ✅ AgentNode UI（头像、状态标签、冒泡框、按钮）
- ✅ SVG 连线 + 动画
- ✅ 视图切换（聊天 ↔ 图形）
- ✅ 直接向子 agent 发消息（前后端）
- ✅ 布局切换动画
- ✅ 9 套主题适配

本 spec **不**涵盖（后续迭代）：
- ❌ 自定义像素精灵图（先用 emoji + CSS 像素化）
- ❌ 画布拖拽/缩放
- ❌ 历史记录回放（图形视图加载历史会话）
- ❌ AgentNode 的 markdown 完整渲染（先纯文本）
