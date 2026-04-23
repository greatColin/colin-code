# coloop-agent

一个**可插拔、模块隔离的轻量级 AGI Agent 底座**，基于 Java 21 + Maven 构建，目标是打磨出一个极简但强大的核心 Agent Loop 内核，服务于 **Vibe Coding** 与 **Spec Coding** 场景。

[English](README.md) | 简体中文

---

## 当前功能

### 1. 核心 Agent Loop
- `AgentLoop.chat()`：经典的 `while(true)` 循环，调用 LLM → 解析 Tool Calls → 执行工具 → 将结果回传给 LLM，直到获得最终文本回复。
- `AgentLoop.chatStream()`：SSE 流式模式，通过 `LLMProvider.StreamConsumer` 回调逐字返回内容，支持流式过程中的 Tool Call 检测与累积。
- 支持最大迭代次数限制（默认 10 轮），防止无限循环。

### 2. 洋葱式四层架构
```
core/           ← 教学核心，永远精简：AgentLoop、抽象接口、数据模型
capability/     ← 可插拔能力模块：Provider、Tool、PromptPlugin、Hook
runtime/        ← 动态组装中枢：CapabilityLoader、StandardCapability、AgentRuntime
entry/          ← 多入口：MinimalDemo（教学）、CliApp（完整功能）
```

### 3. 项目结构

关键包与类说明：

```
com.coloop.agent
├── core/                       ← 教学核心，永远保持精简
│   ├── agent/
│   │   ├── AgentLoop.java      ← 核心 while 循环：LLM → 解析 Tool Calls → 执行 → 回传
│   │   └── AgentHook.java      ← 生命周期钩子接口
│   ├── context/
│   │   ├── ContextCompactor.java      ← 上下文压缩策略接口
│   │   ├── ConversationSummary.java   ← 摘要数据对象
│   │   └── ConversationState.java     ← 跨组件会话状态共享
│   ├── message/
│   │   └── MessageBuilder.java ← 消息构建抽象接口
│   ├── prompt/
│   │   └── PromptPlugin.java   ← 提示词生成抽象接口
│   ├── util/
│   │   └── TokenEstimator.java ← 轻量级 Token 估算工具
│   ├── provider/
│   │   ├── LLMProvider.java    ← LLM 提供商接口
│   │   ├── LLMResponse.java
│   │   └── ToolCallRequest.java
│   ├── tool/
│   │   ├── Tool.java           ← 工具接口
│   │   ├── BaseTool.java
│   │   └── ToolRegistry.java   ← 工具注册与调度
│   ├── command/                ← 命令系统核心接口
│   │   ├── Command.java
│   │   ├── CommandRegistry.java
│   │   ├── CommandContext.java
│   │   ├── CommandResult.java
│   │   └── CommandExitException.java
│   └── interceptor/
│       └── InputInterceptor.java ← 输入拦截器，快捷指令直接短路返回
├── capability/                 ← 可插拔能力实现
│   ├── context/
│   │   └── LLMContextCompactor.java  ← LLM 生成摘要的默认压缩实现
│   ├── message/
│   │   └── StandardMessageBuilder.java ← OpenAI 格式消息组装器
│   ├── prompt/
│   │   ├── PromptSegment.java        ← 系统提示词段落枚举
│   │   ├── BasePromptPlugin.java
│   │   ├── SkillPromptPlugin.java
│   │   ├── AgentsMdPromptPlugin.java
│   │   └── SummaryPromptPlugin.java  ← 将摘要注入 system prompt
│   ├── provider/
│   │   ├── openai/
│   │   │   └── OpenAICompatibleProvider.java
│   │   └── mock/
│   │       └── MockProvider.java
│   ├── tool/
│   │   └── exec/
│   │       └── ExecTool.java
│   ├── hook/
│   │   └── LoggingHook.java
│   └── command/                ← 命令系统实现
│       ├── CommandInterceptor.java     ← 命令拦截器（InputInterceptor 实现）
│       ├── CommandScanner.java         ← 用户自定义命令目录扫描器
│       ├── ExitCommand.java
│       ├── NewSessionCommand.java
│       ├── CompactCommand.java
│       ├── ModelCommand.java
│       └── HelpCommand.java
├── runtime/                    ← 动态组装中枢
│   ├── CapabilityLoader.java   ← 链式组装器
│   ├── StandardCapability.java ← 内置能力目录枚举
│   ├── AgentRuntime.java       ← 组装完成后的可运行代理
│   └── config/
│       └── AppConfig.java
└── entry/                      ← 入口层
    ├── MinimalDemo.java        ← 教学入口（Mock 模式）
    └── CliApp.java             ← 完整入口（真实 API）
```

### 4. 链式能力组装
通过 `CapabilityLoader` 以链式 API 灵活组装 Agent：
```java
new CapabilityLoader()
    .withCapability(StandardCapability.EXEC_TOOL, config)
    .withCapability(StandardCapability.BASE_PROMPT, config)
    .withCapability(StandardCapability.LOGGING_HOOK, config)
    .build(provider, config);
```

### 4. 内置能力
| 能力 | 说明 |
|------|------|
| **ExecTool** | Shell 命令执行（带超时），支持 Windows/Linux 跨平台 |
| **BasePromptPlugin** | 注入基础系统提示（身份、时间、工作目录、平台信息） |
| **SkillPromptPlugin** | 扫描并注入可用技能说明到系统提示 |
| **AgentsMdPromptPlugin** | 自动读取工作目录下的 `AGENTS.md` 并注入系统提示 |
| **LoggingHook** | 在 Agent Loop 关键生命周期节点打印调试日志 |
| **流式输出（后端）** | `LLMProvider.chatStream()` 接口 + SSE 逐字流式传输；`OpenAICompatibleProvider` 实现真实 SSE；支持流式过程中检测并累积 Tool Call |
| **上下文压缩** | `/compact` 将历史消息压缩为摘要并注入 system prompt；自动压缩（超 80% 阈值时保留最近 2 轮）；支持模型级上下文配置（如 minimax 200k / glm 100k） |
| **Command System** | 动态 `Command` 接口 + `CommandRegistry`；内置 `/exit`、`/new`、`/compact`、`/model`、`/help`；支持从 `~/.coloop/commands/` 和 `./.coloop/commands/` 扫描用户自定义命令（项目本地命令覆盖用户命令） |

### 5. 输入拦截器（InputInterceptor）
在 LLM 调用前拦截用户输入，可用于实现快捷指令（如 `/compact`）、Skill 系统、权限确认等直接返回功能。

### 6. Provider 支持
- **MockProvider**：预置响应序列，用于教学、测试、无网环境。
- **OpenAICompatibleProvider**：支持任意 OpenAI 兼容 API（如 OpenRouter、自托管 vLLM）。

### 7. 配置中心
`AppConfig` 支持从环境变量或 JSON 配置文件加载，优先读取 `COLIN_CODE_*` 前缀变量，回退到 `OPENAI_*`：
- `COLIN_CODE_OPENAI_MODEL`
- `COLIN_CODE_OPENAI_API_KEY`
- `COLIN_CODE_OPENAI_API_BASE`
- `COLIN_CODE_MAX_CONTEXT` / `MAX_CONTEXT_SIZE`

上下文大小支持单位后缀：`100k`、`200k`、`1m`，也可直接写数字（如 `8192`）。可在全局或模型级别配置：
```json
{
  "maxContextSize": "100k",
  "models": {
    "minimax": { "maxContextSize": "200k" },
    "glm-4-free": { "maxContextSize": "100k" }
  }
}
```

---

## 快速开始

```bash
# 编译
mvn compile

# 运行教学 Demo（Mock 模式，无需 API Key）
mvn compile exec:java -Dexec.mainClass="com.coloop.agent.entry.MinimalDemo"

# 运行真实 API 模式（需先设置环境变量）
export COLIN_CODE_OPENAI_API_KEY="sk-..."
export COLIN_CODE_OPENAI_API_BASE="https://api.openai.com/v1"
export COLIN_CODE_OPENAI_MODEL="gpt-4o"
mvn compile exec:java -Dexec.mainClass="com.coloop.agent.entry.CliApp"
```

---

## 核心差异分析（聚焦 Agent Loop 内核）

与 Claude Code、Aider、Cline、Codex CLI 等成熟工具相比，`coloop-agent` 目前**只聚焦核心 Loop**，以下是差异盘点（侧重 Vibe Coding / Spec Coding 体验）：

### 我们已具备的（优势）
| 特性 | 说明 |
|------|------|
| **极简内核** | 无 IDE 依赖、无重型框架，代码行数少，适合学习和二次开发 |
| **纯 Java 生态** | 对 Java 开发者友好，便于在企业级 Java 环境中集成 |
| **清晰的插件边界** | `Tool` / `PromptPlugin` / `AgentHook` / `InputInterceptor` 接口明确，扩展不侵入核心 |
| **环境感知提示** | BasePrompt 自动注入时间、OS、工作目录，减少 LLM 的”幻觉” |
| **流式输出就绪** | 后端 SSE 流式已完整实现（`chatStream()` + `StreamConsumer`）；前端 Markdown 渲染、代码高亮、`<think>` 标签分离已完成 |

### 我们缺失的（对 Vibe Coding / Spec Coding 有高价值）

#### 后端 / 核心 Loop
| 缺失能力 | 影响说明 | 优先级 |
|----------|----------|--------|
| **文件系统工具** | ✅ 已实现：`read_file`、`write_file`、`edit_file`、`search_files`、`list_directory` | P0 |
| **对话历史持久化** | ✅ 已实现：`AgentLoop` 维护跨轮次消息列表 | P0 |
| **流式输出（后端）** | ✅ 已实现：`LLMProvider.chatStream()` + `OpenAICompatibleProvider` SSE 逐字输出 | P0 |
| **上下文压缩 / 滑动窗口** | ✅ 已实现：`/compact` 命令 + 自动压缩（80% 阈值）+ TokenEstimator + 模型级上下文配置 | P1 |
| **计划模式（Plan Mode）** | 无法让 Agent 先制定计划、获得用户确认后再执行，容易”先做后错” | P1 |
| **并行 Tool Calls** | OpenAI API 支持一次请求返回多个 tool call，但我们目前串行执行 | P1 |
| **Git 集成** | 无法自动查看 diff、status、生成 commit message、创建分支 | P1 |
| **Checkpoint / 回滚** | 无法像 Aider 一样对代码变更做快照和撤销 | P2 |
| **MCP（Model Context Protocol）支持** | 无法接入外部数据源、数据库、文档系统等标准化接口 | P2 |
| **验证循环（Verify-before-completion）** | 改完代码后不自动编译/运行/测试，无法自证正确性 | P2 |
| **多 Agent 协调** | 单一 Loop 完成所有任务，无法拆分为 Planner + Executor + Reviewer 协作 | P2 |
| **浏览器/截图能力** | 无法验证 Web UI 效果，限制前端开发场景 | P3 |
| **会话恢复** | 进程退出后无法恢复之前的对话状态和未完成的任务 | P3 |

#### 前端 / Web UI
| 缺失能力 | 影响说明 | 优先级 |
|----------|----------|--------|
| **流式输出（前端）** | 后端已支持 SSE，但 `AgentService` 仍调用同步 `chat()`，UI 一次性渲染完整回复 | P0 |
| **Markdown 渲染** | ✅ 已实现：集成 `marked.js` 渲染粗体、列表、链接、表格、代码块；`<think>` 标签提取为可折叠卡片 | P0 |
| **代码语法高亮** | ✅ 已实现：集成 `highlight.js`，全部 9 套主题均适配代码块样式 | P0 |
| **命令系统** | ✅ 已实现：`Command` 接口 + `CommandRegistry` + `CommandInterceptor`；内置 `/exit`、`/new`、`/compact`、`/model`、`/help`；支持从 `~/.coloop/commands/` 和 `./.coloop/commands/` 扫描用户自定义命令 | P1 |
| **斜杠命令自动补全** | ✅ 已实现：后端在 WebSocket 连接时推送可用命令列表；前端输入 `/` 弹出模糊匹配的命令面板并显示描述；支持键盘上下选择、Enter 确认、Esc 关闭 | P1 |
| **会话历史侧边栏** | 仅有一个内存会话，刷新页面即丢失，无 localStorage 持久化 | P1 |
| **模型切换** | `AppConfig` 支持多模型，但用户无法在运行时从 UI 切换 | P1 |
| **消息操作** | 聊天气泡上无复制、重新生成、编辑消息等操作 | P1 |
| **设置面板** | 无 UI 调节温度、max_tokens、字体大小、流式开关 | P2 |
| **欢迎页 / 空状态** | 新会话显示空白聊天区，无介绍和快速启动示例 | P2 |
| **工具结果可视化** | 文件读取、编辑、搜索结果均为纯文本，无 diff 视图、行号、匹配高亮 | P2 |
| **Skill 执行框架** | `SkillPromptPlugin` 只注入说明，无真正的 `/skill` 路由和参数解析 | P2 |
| **导出 / 分享** | 无法将对话保存为 Markdown 或生成分享链接 | P3 |
| **会话内搜索** | 无 Cmd+F 搜索当前聊天内容 | P3 |
| **多模态输入** | 无法上传图片或文件供 LLM 分析 | P3 |

---

## 路线图

### 阶段一：让 Loop 能写代码（后端基础） ✅ 已完成
1. **文件工具集（Filesystem Tools）**
   - ✅ `read_file`：读取文件内容，支持行号范围、偏移量
   - ✅ `write_file`：创建新文件（拒绝覆盖已存在文件）
   - ✅ `edit_file`：基于精确字符串替换的安全编辑
   - ✅ `search_files`：正则表达式内容搜索，支持 glob 过滤
   - ✅ `list_directory`：目录 listing
2. **对话历史持久化** ✅
   - `AgentLoop` 内部维护消息列表，支持多轮 `chat()`
3. **流式输出（后端）** ✅
   - `LLMProvider.chatStream()` 接口，默认退化为同步模式
   - `OpenAICompatibleProvider` 实现 SSE 逐字输出
   - 支持流式中的 Tool Call 检测与累积
4. **Web UI 基础** ✅
   - 基于 WebSocket 的实时聊天界面
   - Thinking、Tool Call、Tool Result 可折叠卡片
   - 9 套主题 + 主题画廊
   - 自动重连与连接状态指示

### 阶段二：前端基础 + 命令系统（当前重点）
5. **命令系统重构** ✅ 已完成
   - ✅ 定义 `Command` 接口 + `CommandRegistry` 动态注册
   - ✅ 将硬编码命令（`/new`、`/exit`）从 `AgentService` 和 `AgentLoopThread` 迁移到注册表
   - ✅ 实现 `/compact`、`/model` 等内置命令
   - ✅ 目录扫描加载用户自定义命令（`~/.coloop/commands/`）和项目本地命令（`./.coloop/commands/`，同名时项目本地优先）
   - ✅ 将 `CommandInterceptor` 接入 `InputInterceptor`，使 `CapabilityLoader` 可组装
   - ✅ 覆盖 86 个单元测试，验证核心接口、所有命令实现、拦截器逻辑、扫描器和运行时集成
6. **流式输出（前端）**
   - `AgentHook` 接口新增 `onStreamChunk()`，用于逐 token 流式通知
   - `AgentService` 从 `agentLoop.chat()` 切换到 `agentLoop.chatStream()`
   - 扩展 `WebSocketLoggingHook`，新增 `onStreamChunk()` 将 SSE 片段通过 WebSocket 推送（`type: stream_chunk`）
   - 前端 `chat.js`：实时追加文本块到渐增的助手消息气泡，循环结束时完成渲染
7. **Markdown 渲染 + 代码高亮** ✅ 已完成
   - ✅ 前端引入 `marked.js` 渲染助手消息
   - ✅ 引入 `highlight.js` 做代码块语法高亮
   - ✅ 使用 `DOMPurify` 对渲染的 HTML 做 XSS 过滤
   - ✅ `<think>` 标签提取为可折叠 Thinking Card
   - ✅ 全部 9 套主题补充 Markdown 元素与代码块样式
8. **斜杠命令自动补全** ✅ 已完成
   - ✅ 后端在 WebSocket 连接时推送可用命令列表
   - ✅ 前端输入 `/` 弹出模糊匹配的命令面板，显示描述
   - ✅ 键盘导航（方向键、Enter、Esc）
9. **模型切换**
   - 后端在 WebSocket 连接时暴露可用模型
   - 前端在主题切换器旁添加模型下拉框
   - 切换时重建该会话的 `LLMProvider` 使用新 `ModelConfig`
10. **消息操作**
    - 复制消息到剪贴板
    - 重新生成最后一条助手回复
    - 编辑历史用户消息并重新运行 Loop

### 阶段三：会话管理 + 后端可靠性
11. **会话历史侧边栏**
    - localStorage / IndexedDB 持久化会话元数据和消息
    - 左侧边栏：会话列表，显示标题、时间戳、消息数
    - 新建 / 删除 / 重命名会话；自动以第一条用户消息生成标题
    - 点击历史项恢复上下文（将消息重播进 `AgentLoop`）
12. **计划模式（Plan Mode）**
    - 检测到复杂任务时，Agent 先输出计划，用户确认后再执行
    - 与 `InputInterceptor` 结合，支持 `/plan` 快捷指令
13. **并行 Tool Calls**
    - 一轮 LLM 响应中多个 tool call 并行执行，缩短总耗时
14. **上下文管理** ✅ 已完成
    - ✅ `/compact` 命令：将历史消息压缩为摘要并注入 system prompt
    - ✅ 自动压缩：token 占用超 80% 阈值时自动触发，保留最近 2 轮
    - ✅ TokenEstimator：基于字符数的轻量级 token 估算（中文 1.5 / 非中文 0.25）
    - ✅ 模型级上下文配置：支持 `maxContextSize` 字段，单位为无单位/`k`/`m`（如 minimax 配 200k，glm 配 100k，默认 100k）
15. **Git 集成工具**
    - `git_status`、`git_diff`、`git_commit`、`git_branch`
    - 自动在关键操作前查看 diff，防止误改

### 阶段四：前端打磨 + 进阶能力
16. **设置面板**
    - 温度、max_tokens、流式开关
    - 字体大小等 UI 偏好
    - 持久化到 localStorage
17. **欢迎页 / 空状态**
    - 新会话显示能力介绍和示例提示
    - 快速启动按钮（”分析项目结构”、”写一个 Hello World”）
18. **工具结果可视化**
    - ReadFileTool：带语法高亮和行号的代码展示
    - EditFileTool：红绿对比的 diff 视图
    - SearchFilesTool：高亮匹配行，文件路径可点击
    - ExecTool：终端风格输出，带退出码颜色
19. **Skill 系统完整化**
    - Skill 注册表 + 路由解析
    - 支持用户自定义 Skill（如 `/tdd`、`/review`）
20. **验证循环**
    - 代码修改后自动执行 `mvn compile` 或测试套件
    - 失败时将错误信息自动回传给 LLM 进行修复
21. **MCP Client 支持**
    - 接入外部 MCP Server，扩展工具边界

### 阶段五：生态与可扩展性
22. **Checkpoint 与回滚**
    - 基于 Git 工作区或内存快照的变更回滚
23. **多 Agent 协调**
    - 在现有 Hook 体系上，支持子 Agent / 专属 Loop 的委派
24. **导出 / 分享**
    - 导出对话为 Markdown 文件
    - 生成可分享链接（需后端会话持久化）
25. **浏览器工具**
    - 基于 Playwright 或 Selenium 的截图与操作验证
26. **多模态输入**
    - 为支持视觉的模型上传图片和文件

---

## 设计理念

> **核心层永远精简，能力层无限扩展。**

`coloop-agent` 不是要成为第二个 Claude Code，而是要成为一个**透明、可理解、可魔改**的 Agent Loop 内核。当你想搞懂“一个 Coding Agent 到底是怎么工作的”，或者想“从零开始为自己的团队定制一个 Agent”，这里就是最好的起点。

---

## License

MIT
