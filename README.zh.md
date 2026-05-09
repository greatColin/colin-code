# coloop-agent

一个**可插拔、模块隔离的轻量级 Java AGI Agent 平台**——极简但强大的核心 Agent Loop 内核，配合 Spring Boot Web UI 与独立的语音输入服务，专为 **Vibe Coding** 与 **Spec Coding** 场景打造。

[English](README.md) | 简体中文

---

## 仓库结构

`coloop-agent` 是一个 Maven 多模块项目，外加一个独立的 Python 服务：

```
coloop-agent/
├── coloop-agent-core/    ← Java agent 内核：core/ + capability/ + runtime/ + entry/
├── coloop-agent-server/  ← Spring Boot WebSocket 服务端 + Web UI（依赖 core）
├── coloop-agent-voice/   ← 独立 Python 语音输入服务（faster-whisper）
├── docs/                 ← 设计稿、实现计划、参考资料
└── pom.xml               ← 父 POM（Java 17，Spring Boot 3.2.5）
```

两个 Java 模块共用 `coloop-agent-setting.json`（位于 `coloop-agent-core/src/main/resources/`），统一管理模型、MCP、语音配置。

---

## 当前功能

### 1. 核心 Agent Loop
- `AgentLoop.chat()`：经典 `while(true)` 循环，调用 LLM → 解析 Tool Calls → 执行工具 → 将结果回传给 LLM，直到获得最终文本回复。
- `AgentLoop.chatStream()`：SSE 流式模式，通过 `LLMProvider.StreamConsumer` 回调逐字返回内容，支持流式过程中的 Tool Call 检测与累积。
- `AgentLoopThread`：线程包装器，供服务端实现可中断的运行（`/stop` 命令）。
- 支持最大迭代次数限制（默认 50 轮），防止无限循环。

### 2. 洋葱式四层架构
```
core/           ← 教学核心，永远精简：AgentLoop、抽象接口、数据模型、ConversationState
capability/     ← 可插拔能力模块：Provider、Tool、PromptPlugin、Hook、Command、MCP、Subagent、Task、Plan、History
runtime/        ← 动态组装中枢：CapabilityLoader、StandardCapability、CompositeCapability、AgentRuntime
entry/          ← 多入口：MinimalDemo（教学）、CliApp（完整功能）
```

### 3. 项目结构

`coloop-agent-core` 关键包与类：

```
com.coloop.agent
├── core/                                ← 教学核心，永远保持精简
│   ├── agent/
│   │   ├── AgentLoop.java               ← 核心 while 循环：LLM → 解析 Tool Calls → 执行 → 回传
│   │   ├── AgentLoopThread.java         ← 服务端使用的可中断 Loop 包装器
│   │   └── AgentHook.java               ← 生命周期钩子（含 onStreamChunk、onSubagent*）
│   ├── context/
│   │   ├── ContextCompactor.java        ← 上下文压缩策略接口
│   │   ├── ConversationSummary.java     ← 摘要数据对象
│   │   ├── ConversationState.java       ← 跨组件会话状态（含 SubagentRegistry、计划）
│   │   └── PlanTask.java                ← 计划模式下的任务记录
│   ├── history/                         ← 新增：会话持久化基础
│   │   ├── HistoryMessage.java
│   │   ├── SessionMeta.java
│   │   ├── ConversationHistoryStore.java
│   │   ├── FileSystemHistoryStore.java
│   │   └── HistoryRecordingHook.java
│   ├── command/                         ← 命令系统核心接口
│   │   ├── Command.java / CommandRegistry.java / CommandContext.java
│   │   ├── CommandResult.java / CommandExitException.java
│   ├── interceptor/InputInterceptor.java
│   ├── message/MessageBuilder.java
│   ├── prompt/PromptPlugin.java
│   ├── provider/{LLMProvider, LLMResponse, ToolCallRequest}.java
│   ├── task/{Task, TaskStatus, TaskStore}.java   ← 新增：任务数据模型
│   ├── tool/{Tool, BaseTool, ToolRegistry}.java
│   └── util/TokenEstimator.java
├── capability/                          ← 可插拔能力实现
│   ├── command/                         ← 内置命令 + 扫描器
│   │   ├── ExitCommand / NewSessionCommand / CompactCommand
│   │   ├── ModelCommand / HelpCommand / StopCommand
│   │   ├── CommandInterceptor / CommandScanner
│   │   ├── MdCommandParser / MdPromptCommand / MdCommandDefinition
│   ├── context/LLMContextCompactor.java
│   ├── hook/{LoggingHook, ClaudeCodeStyleLoggingHook, AnsiColors}.java
│   ├── mcp/                             ← MCP 客户端（STDIO + JSON-RPC）
│   │   ├── McpCapability / McpClient / McpTransport
│   │   ├── McpToolAdapter / McpToolDefinition
│   │   └── JsonRpcRequest / JsonRpcResponse / McpException
│   ├── message/StandardMessageBuilder.java
│   ├── plan/                            ← /plan 模式
│   │   ├── PlanCapability / PlanCommand / CancelCommand
│   │   ├── PlanInjectionHook / PlanPromptPlugin
│   ├── prompt/{Base, AgentsMd, Skill, Summary}PromptPlugin + PromptSegment
│   ├── provider/{openai/OpenAICompatibleProvider, mock/MockProvider}
│   ├── subagent/                        ← 新增：命名子 Agent 系统
│   │   ├── SubagentInstance / SubagentRegistry / SubagentEventListener
│   │   ├── SubagentLoopFactory / SubagentPromptPlugin
│   │   ├── AgentTool / SendMessageTool / ListModelsTool
│   │   └── SubagentManagementCapability
│   ├── task/                            ← 新增：任务管理能力
│   │   ├── TaskCreateTool / TaskUpdateTool / TaskGetTool / TaskListTool
│   │   ├── TaskService / InMemoryTaskStore
│   │   ├── TaskStatusPromptPlugin / TaskDisplayHook
│   │   ├── TaskManagementCapability
│   │   └── command/TasksCommand
│   └── tool/
│       ├── exec/ExecTool.java
│       └── filesystem/{Read, Write, Edit, Search, ListDirectory}FileTool
├── runtime/
│   ├── CapabilityLoader.java            ← 链式组装器，支持 snapshotTools()
│   ├── CompositeCapability.java         ← 在一个入口下打包多个能力
│   ├── StandardCapability.java          ← 内置能力目录枚举
│   ├── runtime/{AgentRuntime, LoopInputAgentRuntime}.java
│   └── config/AppConfig.java            ← 加载 coloop-agent-setting.json（支持 JSON 注释）
└── entry/
    ├── MinimalDemo.java                 ← 教学入口（Mock 模式）
    └── CliApp.java                      ← 完整 CLI 入口（真实 API + 命令 + MCP）
```

`coloop-agent-server` 关键包：

```
com.coloop.agent.server
├── config/        ← Spring 配置
├── controller/    ← REST 接口（如 /api/config）
├── dto/           ← WebSocketMessage，含所有事件类型的工厂方法
├── hook/          ← AbstractWebSocketLoggingHook + WebSocketLoggingHook + SubagentLoggingHook
├── service/       ← AgentService：每会话的 AgentRuntime 组装
└── websocket/     ← AgentWebSocketHandler：聊天/历史/子 Agent/Toast 路由
```

前端（位于 `coloop-agent-server/src/main/resources/static/`）：`index.html`、`chat.js`、`topology.js`、`group-chat.js`、`graph-state.js`、themes/，以及用于预览全部 9 套主题的 `theme-gallery.html`。

### 4. 链式能力组装
通过 `CapabilityLoader` 以链式 API 灵活组装 Agent：
```java
new CapabilityLoader()
    .withCapability(StandardCapability.EXEC_TOOL, config)
    .withCapability(StandardCapability.READ_FILE_TOOL, config)
    .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
    .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
    .withCapability(StandardCapability.MCP_CLIENT, config)
    .withCapability(StandardCapability.TASK_MANAGEMENT, config)
    .withCapability(StandardCapability.BASE_PROMPT, config)
    .withCapability(StandardCapability.LOGGING_HOOK, config)
    .build(provider, config);
```

### 5. 内置能力
| 能力 | 说明 |
|------|------|
| **ExecTool** | Shell 命令执行（带超时），支持 Windows/Linux 跨平台 |
| **文件系统工具** | `read_file`、`write_file`、`edit_file`、`search_files`、`list_directory`（位于 `capability/tool/filesystem`） |
| **BasePromptPlugin** | 注入基础系统提示（身份、时间、工作目录、平台信息） |
| **SkillPromptPlugin** | 扫描并注入可用技能说明到系统提示 |
| **AgentsMdPromptPlugin** | 自动读取工作目录下的 `AGENTS.md` 并注入系统提示 |
| **LoggingHook / ClaudeCodeStyleLoggingHook** | 在 Agent Loop 关键生命周期节点打印调试日志，可使用 Claude Code 风格格式 |
| **流式输出** | `LLMProvider.chatStream()` + `OpenAICompatibleProvider` 真实 SSE；流式过程中检测并累积 Tool Call |
| **上下文压缩** | `/compact` 将历史消息压缩为摘要并注入 system prompt；超 80% Token 阈值时自动压缩；模型级 `maxContextSize`（如 minimax 200k、glm 100k） |
| **命令系统** | 动态 `Command` 接口 + `CommandRegistry`；内置 `/exit`、`/new`、`/compact`、`/model`、`/help`、`/plan`、`/cancel`、`/stop`、`/tasks`；支持从 `~/.coloop/commands/` 与 `./.coloop/commands/` 扫描用户自定义命令（JSON 定义静态/Shell，**Markdown** 定义提示词模板并支持 `$ARGUMENTS`；项目本地命令覆盖用户级） |
| **计划模式（Plan Mode）** | `/plan` 进入只读规划模式，使用独立 AgentLoop（仅 read/search/list/exec 工具）；计划存入 `ConversationState`，确认后注入主循环；`/cancel` 取消 |
| **MCP 客户端** | `McpClient` 通过 STDIO + JSON-RPC 连接 MCP Server；`McpCapability` 自动将远程工具注册到本地 ToolRegistry；通过 `AppConfig.mcpServers` 配置 |
| **子 Agent 系统（新）** | 命名多 Agent 协作：`AgentTool` 创建/替换子 Agent，`SendMessageTool` 向已有子 Agent 追加消息，`ListModelsTool` 查询可用模型。`SubagentRegistry` 管理生命周期，`SubagentLoggingHook` 将每个子 Agent 的事件路由到 WebSocket。子 Agent 可指定不同 `model`，未知 key 时通过 toast 提示 fallback。 |
| **任务管理（新）** | `TaskCreateTool / TaskUpdateTool / TaskGetTool / TaskListTool` 加上 `TaskStatusPromptPlugin` 和 `TaskDisplayHook`；仅在 ≥3 步时触发，仅限主 Agent 使用；`/tasks` 命令在 CLI/Web 中展示 |
| **会话历史持久化（新）** | `ConversationHistoryStore` + `FileSystemHistoryStore` 将会话元数据与消息写入磁盘；`HistoryRecordingHook` 捕获生命周期事件；Web 侧边栏列出历史会话，点击即可恢复上下文 |

### 6. 输入拦截器（InputInterceptor）
在 LLM 调用前拦截用户输入，驱动 `/` 命令快捷指令、计划模式、Skill 路由、权限确认等直接返回功能。

### 7. Provider 支持
- **MockProvider**：预置响应序列，用于教学、测试、无网环境。
- **OpenAICompatibleProvider**：支持任意 OpenAI 兼容 API（如 OpenRouter、Minimax、GLM、自托管 vLLM），完整支持 SSE 流式输出。

### 8. 配置中心
`AppConfig` 从 `coloop-agent-core/src/main/resources/coloop-agent-setting.json` 加载（支持 `${VAR}` 环境变量插值），并支持 **JSON 行注释与块注释**（`//` 与 `/* */`）。结构：

```jsonc
{
  // 全局默认模型 — 当 subagent 未指定 model 时使用
  "defaultModel": "minimax",
  "maxIterations": 50,
  "execTimeoutSeconds": 30,

  "models": {
    "minimax": {
      "description": "主模型 — 推理能力强，适合复杂任务",
      "apiKey": "${COLIN_CODE_MINIMAX_API_KEY}",
      "apiBase": "https://api.minimaxi.com/v1",
      "model": "MiniMax-M2.7",
      "maxContextSize": "200k"
    },
    "glm-4-free": {
      "description": "免费轻量模型 — 适合简单任务",
      "apiKey": "${COLIN_CODE_GLM_API_KEY}",
      "apiBase": "https://open.bigmodel.cn/api/paas/v4",
      "model": "GLM-4.7-Flash",
      "maxContextSize": "100k"
    }
  },

  "mcpServers": {
    "MiniMax": { "command": "uvx", "args": ["minimax-coding-plan-mcp"], "env": { ... } }
  },

  "voice": {
    "transcription": { "strategy": "local_whisper", "strategies": { ... } },
    "correction":    { "strategy": "llm",           "strategies": { ... } },
    "language": "zh",
    "recognitionMode": "realtime",
    "coloopServer": { "wsUrl": "ws://localhost:8080/ws/agent" }
  }
}
```

`maxContextSize` 支持单位后缀（`100k`、`200k`、`1m`），也可直接写数字。

---

## 快速开始

### 编译全部模块

```bash
mvn clean install -DskipTests
```

### 运行教学 Demo（无需 API Key）

```bash
cd coloop-agent-core
mvn exec:java -Dexec.mainClass="com.coloop.agent.entry.MinimalDemo"
```

### 运行 CLI（真实 API）

```bash
export COLIN_CODE_MINIMAX_API_KEY="sk-..."
# 或 COLIN_CODE_OPENAI_API_KEY / COLIN_CODE_GLM_API_KEY 等
cd coloop-agent-core
mvn exec:java -Dexec.mainClass="com.coloop.agent.entry.CliApp"
```

### 运行 Web UI 服务端

```bash
cd coloop-agent-server
mvn spring-boot:run
# 浏览器打开 http://localhost:8080/
```

### 运行语音输入服务（可选）

```bash
cd coloop-agent-voice
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
python main.py
# 浏览器打开 http://localhost:8000/static/voice-input.html
```

语音服务通过 `voice.coloopServer.wsUrl` 配置的 WebSocket URL 把识别后的文本流推送给 agent server。

---

## 功能总览

按能力域分组，从底层 Loop 逐步递进到上层 UX。状态：✅ 已实现 · ⚠️ 部分实现 · ⏳ 计划中。

### 1. Agent Loop 核心
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 同步 chat 循环 | ✅ | `AgentLoop.chat()` — LLM → 解析 Tool Calls → 执行 → 回传 |
| 流式 chatStream | ✅ | `chatStream()` SSE 逐字 + 流式中 Tool Call 累积 |
| 可中断运行 | ✅ | `AgentLoopThread` + `/stop` 命令 |
| 最大迭代限制 | ✅ | 默认 50 轮，可配置 |
| 并行 Tool Calls | ⏳ | OpenAI 单次响应支持多个 tool call，目前串行执行 |
| 验证循环（编译/测试自检） | ⏳ | 改完代码后自动验证并把错误回传 |

### 2. Provider 与模型管理
| 功能 | 状态 | 说明 |
|------|:----:|------|
| MockProvider | ✅ | 预置响应序列，用于教学和离线测试 |
| OpenAI 兼容 Provider | ✅ | 任意 OpenAI 兼容 API + 真实 SSE 流式 |
| 多模型配置 | ✅ | `models.*` + 全局 `defaultModel` |
| 运行时模型切换 | ✅ | `/model` 重建会话的 `LLMProvider` |
| 子 Agent 独立模型 | ✅ | `AgentTool` 接受 `model` 参数；未知 key 时 toast 提示并回落到默认 |
| 模型查询工具 | ✅ | `ListModelsTool` 把已配置模型暴露给 LLM |
| 模型 `description` 元数据 | ✅ | 在 UI 与 `ListModelsTool` 中展示 |

### 3. 工具与外部集成
| 功能 | 状态 | 说明 |
|------|:----:|------|
| Shell 执行 | ✅ | `ExecTool`，跨平台，超时可配 |
| 文件读 | ✅ | `read_file` 支持行号范围 / 偏移 |
| 文件写（仅创建） | ✅ | `write_file` 拒绝覆盖已存在文件 |
| 文件编辑 | ✅ | `edit_file` 精确字符串替换 |
| 文件搜索 | ✅ | `search_files` 正则 + glob 过滤 |
| 目录列出 | ✅ | `list_directory` |
| MCP 客户端 | ✅ | STDIO + JSON-RPC，自动把远程工具注册成本地工具 |
| 工具结果 diff / 语法视图 | ⏳ | 当前为纯文本展示 |
| 浏览器 / 截图工具 | ⏳ | Playwright / Selenium 集成 |

### 4. Prompt 工程
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 环境感知 BasePrompt | ✅ | 注入身份、时间、OS、工作目录 |
| AGENTS.md 自动注入 | ✅ | 读取项目本地 `AGENTS.md` |
| 摘要注入 | ✅ | `SummaryPromptPlugin` 把压缩摘要写入 system prompt |
| Skill 描述注入 | ⚠️ | 仅注入说明，无 `/skill` 路由 |
| 任务状态注入 | ✅ | `TaskStatusPromptPlugin` 让 LLM 同步 todo 状态 |
| 计划注入 | ✅ | `PlanPromptPlugin` 在确认后写入主循环 |
| 子 Agent 隔离 prompt | ✅ | `SubagentPromptPlugin` |

### 5. 命令与输入路由
| 功能 | 状态 | 说明 |
|------|:----:|------|
| `Command` 接口 + `CommandRegistry` | ✅ | 动态注册 |
| 内置命令 | ✅ | `/exit`、`/new`、`/compact`、`/model`、`/help`、`/plan`、`/cancel`、`/stop`、`/tasks` |
| 用户级 JSON 命令 | ✅ | `~/.coloop/commands/*.json` 定义静态 / Shell 命令 |
| Markdown 提示词命令 | ✅ | YAML frontmatter + `$ARGUMENTS` 插值 |
| 项目本地命令覆盖 | ✅ | `./.coloop/commands/` 同名优先 |
| 斜杠自动补全 | ✅ | 后端推送命令列表，前端模糊匹配 + 键盘导航 |

### 6. 上下文管理
| 功能 | 状态 | 说明 |
|------|:----:|------|
| `TokenEstimator` | ✅ | 中文 ~1.5 / 非中文 ~0.25 字符/token |
| 模型级 `maxContextSize` | ✅ | 接受 `100k` / `200k` / `1m` / 数字 |
| 手动压缩 | ✅ | `/compact` |
| 80% 自动压缩 | ✅ | 保留最近 2 轮 |
| 实时上下文使用条 | ✅ | 每会话一条，每子 Agent 一条 |

### 7. 计划模式（Plan Mode）
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 只读规划循环 | ✅ | `/plan` 启动独立 AgentLoop，仅 read/search/list/exec 工具 |
| 计划存入会话状态 | ✅ | `ConversationState` 中的 `PlanTask` 数据模型 |
| 主循环注入 | ✅ | 用户确认后通过 `PlanInjectionHook` 注入 |
| 取消待执行计划 | ✅ | `/cancel` |

### 8. 会话历史与持久化
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 跨轮次内存历史 | ✅ | `AgentLoop` 维护跨 `chat()` 的消息列表 |
| 文件级会话存储 | ✅ | `FileSystemHistoryStore` 写入消息 + 元数据 |
| 历史录制 Hook | ✅ | `HistoryRecordingHook` 捕获生命周期事件 |
| 历史侧边栏 | ✅ | 折叠列表，点击恢复 |
| 子 Agent 注册表恢复 | ✅ | 加载历史时一并恢复 |
| 上下文使用持久化 | ✅ | 跨刷新保留 |
| 导出 Markdown | ⏳ | |
| 分享链接 | ⏳ | 需服务端会话分享 |
| 会话内搜索 | ⏳ | |

### 9. 多 Agent 协调
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 子 Agent 数据模型 | ✅ | `SubagentInstance` |
| 线程安全注册表 | ✅ | `SubagentRegistry`，含 createOrReplace / clear |
| 创建 / 替换工具 | ✅ | `AgentTool` |
| 持续对话工具 | ✅ | `SendMessageTool` 向已有子 Agent 追加消息 |
| 每 Agent 状态隔离 | ✅ | 各自的消息列表、上下文条、Hook |
| 事件路由 | ✅ | `SubagentLoggingHook` 把每 Agent 事件推到 WebSocket |
| Agent 侧边栏 | ✅ | 列出运行中的子 Agent 与状态 |
| 拓扑画布 | ✅ | 节点 + 连线 + 状态标签 |
| 群聊模式 | ✅ | 多 Agent 同屏对话 |
| Combined Mode UI | ✅ | 在聊天 / 拓扑 / 群聊间切换 |

### 10. 任务管理
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 任务数据模型 | ✅ | `Task` + `TaskStatus` |
| 任务工具集 | ✅ | `TaskCreateTool` / `TaskUpdateTool` / `TaskGetTool` / `TaskListTool` |
| `/tasks` 命令 | ✅ | CLI + Web 同步展示 |
| 状态注入 | ✅ | `TaskStatusPromptPlugin` |
| 显示 Hook | ✅ | `TaskDisplayHook` 把任务卡片写入 WebSocket |
| 3+ 步阈值 | ✅ | 仅多步任务才触发 |
| 主 Agent 限定 | ✅ | 子 Agent 不参与任务管理 |

### 11. Web UI
| 功能 | 状态 | 说明 |
|------|:----:|------|
| WebSocket 实时聊天 | ✅ | 自动重连 + 连接状态指示 |
| 流式增量渲染 | ✅ | 防抖 100 ms / 50 字符 + 闪烁光标 |
| Markdown 渲染 | ✅ | `marked.js` |
| 代码语法高亮 | ✅ | `highlight.js` |
| XSS 过滤 | ✅ | `DOMPurify` |
| Thinking 卡片 | ✅ | `<think>` 标签提取折叠 |
| 9 套主题 + 主题画廊 | ✅ | 全部主题适配代码块和侧边栏分区 |
| 斜杠命令面板 | ✅ | 模糊匹配 + 键盘导航 |
| 历史会话侧边栏 | ✅ | 折叠列表，点击恢复 |
| Agent 侧边栏 | ✅ | 子 Agent 状态 |
| 拓扑 / 群聊 / Combined Mode | ✅ | Graph View 画布 |
| 模型 fallback Toast | ✅ | 子 Agent model key 未知时提示 |
| 任务展示卡片 | ✅ | 与 CLI 共用渲染 |
| 消息操作 | ⏳ | 复制 / 重新生成 / 编辑后重跑 |
| 设置面板 | ⏳ | 温度、字号、流式开关等 |
| 欢迎页 / 空状态 | ⏳ | 介绍 + 快速启动示例 |
| 工具结果可视化 | ⏳ | diff / 行号 / 终端风格输出 |
| Skill 路由 | ⏳ | 当前仅注入描述 |
| 会话内搜索 | ⏳ | Cmd+F 搜索当前对话 |
| 多模态上传 | ⏳ | 图片 / 文件输入 |

### 12. 语音输入（独立 Python 服务）
| 功能 | 状态 | 说明 |
|------|:----:|------|
| faster-whisper 引擎 | ✅ | 多种模型尺寸；CPU 或 CUDA |
| EnergyVAD | ✅ | 基于 RMS 的语音活动检测 |
| 多识别模式 | ✅ | `realtime` / `realtime_final` / `final_only` |
| 策略接口 | ✅ | `TranscriptionStrategy` + `CorrectionStrategy` |
| `VoiceFactory` 装配 | ✅ | 按配置组装策略 |
| 流式纠错 | ✅ | 识别过程中实时修正 |
| LLM 后处理纠错 | ✅ | 复用 `coloop-agent-setting.json` 模型配置 |
| HTTP / WebSocket 转写适配 | ✅ | 本地 Whisper 之外的可插拔后端 |
| 液态玻璃 UI | ✅ | 麦克风一键录音 + 波形可视化 |
| 推送给 agent server | ✅ | 通过 WebSocket 流式发送识别结果 |

### 13. 配置中心
| 功能 | 状态 | 说明 |
|------|:----:|------|
| `${VAR}` 环境变量插值 | ✅ | API key、URL 等 |
| JSON 行 / 块注释 | ✅ | `//` 与 `/* */` |
| 模型 `description` 字段 | ✅ | UI 与 `ListModelsTool` 展示 |
| 全局 `defaultModel` | ✅ | 子 Agent 未指定 model 时使用 |
| `maxContextSize` 单位后缀 | ✅ | `k` / `m` / 数字 |
| MCP 服务端配置 | ✅ | command + args + env（`env` 支持 `${models.*.apiKey}`） |
| 语音模块配置 | ✅ | 转写 / 纠错策略、识别模式、coloop WS URL |

---

## 设计理念

> **核心层永远精简，能力层无限扩展。**

`coloop-agent` 不是要成为第二个 Claude Code，而是要成为一个**透明、可理解、可魔改**的 Agent Loop 平台。内核足够小，一个下午就能读完；新能力（子 Agent、历史、语音、MCP）通过定义清晰的接口接入，永远不侵蚀核心。当你想搞懂"一个 Coding Agent 到底是怎么工作的"，或者想"从零开始为自己的团队定制一个 Agent"，这里就是最好的起点。

---

## License

MIT
