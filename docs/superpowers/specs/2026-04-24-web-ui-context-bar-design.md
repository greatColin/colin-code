# Web UI 上下文进度条与消息顺序修复设计

## 背景

当前 Web 聊天界面存在两个问题：
1. **消息顺序错误**：streaming 模式下，`<think>` 卡片被追加到已存在的 assistant 消息之后，导致过程信息显示在最终回复下方。
2. **缺少上下文占用指示**：用户无法直观看到当前对话占用了多少 token 上下文。

## 目标

1. 修复消息顺序：think、tool_call、tool_result 等过程信息应显示在 assistant 最终回复的上方。
2. 在页面最上方增加上下文占用指示器，包含当前 token 数、最大限制、百分比和进度条。

## 方案

采用**最小改动方案**：不重构消息 DOM 结构，仅通过 insertBefore 修正顺序；后端新增 `context_usage` WebSocket 消息类型。

## 变更详情

### 1. 后端：WebSocketLoggingHook

- 构造函数增加 `AgentLoop` 参数，用于获取上下文统计信息。
- `beforeLLMCall` 中发送 `context_usage` 消息，payload 包含：
  - `tokens`: 当前估算 token 数
  - `limit`: 最大上下文限制
  - `percent`: 占用百分比 (0-100)
- 新增 `WebSocketMessage.contextUsage(int tokens, int limit, int percent)` 工厂方法。

### 2. 后端：AgentService

- 创建 `WebSocketLoggingHook` 时传入 `agentLoop` 实例。

### 3. 前端：index.html

- 在 `<header>` 后新增上下文进度条 DOM：
  ```html
  <div class="context-bar" id="context-bar">
      <div class="context-info">
          <span class="context-label">上下文占用</span>
          <span class="context-value" id="context-value">0 / 100K (0%)</span>
      </div>
      <div class="context-progress-bg">
          <div class="context-progress-fill" id="context-progress-fill" style="width:0%"></div>
      </div>
  </div>
  ```

### 4. 前端：chat.js

- 新增 `insertBeforeAssistant(el)` 辅助函数：若 `currentAssistantEl` 存在且在 DOM 中，调用 `chatContainer.insertBefore(el, currentAssistantEl)`，否则回退到 `appendChild`。
- `renderThinking`、`renderToolCall`、`renderToolResult` 改用 `insertBeforeAssistant` 替代 `appendElement`。
- `handleMessage` 新增 `case 'context_usage'`：
  - 更新 `#context-value` 文本
  - 更新 `#context-progress-fill` 宽度
  - 根据 percent 切换 CSS class：`low` (<50%)、`mid` (50-80%)、`high` (>80%)

### 5. 样式：主题 CSS

所有主题文件追加 `.context-bar`、`.context-progress-bg`、`.context-progress-fill` 及相关变体样式：
- **low** (`<50%`)：绿色系进度条
- **mid** (`50-80%`)：黄色/橙色系进度条
- **high** (`>80%`)：红色系进度条

## 数据流

```
用户发送消息
    → AgentLoop.chatStream()
    → beforeLLMCall(messages)
        → WebSocketLoggingHook 读取 agentLoop.getCurrentTokenCount() / getContextLimit() / getContextUsagePercent()
        → 发送 {type: "context_usage", payload: {tokens, limit, percent}}
    → 前端 handleMessage()
        → 更新 #context-value 和 #context-progress-fill
    → LLM streaming / tool calls / thinking
        → 前端按 insertBeforeAssistant 规则插入卡片
    → assistant 最终回复
        → 更新 currentAssistantEl 内容
```

## 测试要点

1. streaming 模式下发送包含 `<think>` 标签的回复，确认 think 卡片在 assistant 消息上方。
2. 工具调用链中，确认 tool_call / tool_result 卡片在 assistant 消息上方。
3. 进度条在不同上下文占用率下显示正确的颜色和百分比。
4. 切换主题后进度条样式保持一致。
