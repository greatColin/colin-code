# coloop-agent

一个**可插拔、模块隔离的轻量级 AGI Agent 底座**，基于 Java 21 + Maven 构建，目标是打磨出一个极简但强大的核心 Agent Loop 内核，服务于 **Vibe Coding** 与 **Spec Coding** 场景。

[English](README.md) | 简体中文

---

## 当前功能

### 1. 核心 Agent Loop
- `AgentLoop.chat()`：经典的 `while(true)` 循环，调用 LLM → 解析 Tool Calls → 执行工具 → 将结果回传给 LLM，直到获得最终文本回复。
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
│   ├── message/
│   │   └── MessageBuilder.java ← 消息构建抽象接口
│   ├── prompt/
│   │   └── PromptPlugin.java   ← 提示词生成抽象接口
│   ├── provider/
│   │   ├── LLMProvider.java    ← LLM 提供商接口
│   │   ├── LLMResponse.java
│   │   └── ToolCallRequest.java
│   ├── tool/
│   │   ├── Tool.java           ← 工具接口
│   │   ├── BaseTool.java
│   │   └── ToolRegistry.java   ← 工具注册与调度
│   └── interceptor/
│       └── InputInterceptor.java ← 输入拦截器，快捷指令直接短路返回
├── capability/                 ← 可插拔能力实现
│   ├── message/
│   │   └── StandardMessageBuilder.java ← OpenAI 格式消息组装器
│   ├── prompt/
│   │   ├── PromptSegment.java        ← 系统提示词段落枚举
│   │   ├── BasePromptPlugin.java
│   │   ├── SkillPromptPlugin.java
│   │   └── AgentsMdPromptPlugin.java
│   ├── provider/
│   │   ├── openai/
│   │   │   └── OpenAICompatibleProvider.java
│   │   └── mock/
│   │       └── MockProvider.java
│   ├── tool/
│   │   └── exec/
│   │       └── ExecTool.java
│   └── hook/
│       └── LoggingHook.java
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

### 5. 输入拦截器（InputInterceptor）
在 LLM 调用前拦截用户输入，可用于实现快捷指令（如 `/compact`）、Skill 系统、权限确认等直接返回功能。

### 6. Provider 支持
- **MockProvider**：预置响应序列，用于教学、测试、无网环境。
- **OpenAICompatibleProvider**：支持任意 OpenAI 兼容 API（如 OpenRouter、自托管 vLLM）。

### 7. 配置中心
`AppConfig` 支持从环境变量加载，优先读取 `COLIN_CODE_*` 前缀变量，回退到 `OPENAI_*`：
- `COLIN_CODE_OPENAI_MODEL`
- `COLIN_CODE_OPENAI_API_KEY`
- `COLIN_CODE_OPENAI_API_BASE`

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
| **环境感知提示** | BasePrompt 自动注入时间、OS、工作目录，减少 LLM 的“幻觉” |

### 我们缺失的（对 Vibe Coding / Spec Coding 有高价值）
| 缺失能力 | 影响说明 | 优先级 |
|----------|----------|--------|
| **文件系统工具** | 无法读、写、编辑、搜索代码文件，这是 Coding Agent 的根基 | P0 |
| **对话历史持久化** | 每次 `chat()` 独立，无法在多轮对话中保持上下文和记忆 | P0 |
| **流式输出（Streaming）** | 用户需等待整段回复生成完毕，体验远不如逐字输出 | P0 |
| **计划模式（Plan Mode）** | 无法让 Agent 先制定计划、获得用户确认后再执行，容易“先做后错” | P1 |
| **并行 Tool Calls** | OpenAI API 支持一次请求返回多个 tool call，但我们目前串行执行 | P1 |
| **上下文压缩 / 滑动窗口** | 长会话会导致消息列表膨胀，最终超出模型上下文限制 | P1 |
| **Skill 执行框架** | 现有 `SkillPromptPlugin` 只注入说明，没有真正的 `/skill` 路由和参数解析 | P1 |
| **Git 集成** | 无法自动查看 diff、status、生成 commit message、创建分支 | P1 |
| **Checkpoint / 回滚** | 无法像 Aider 一样对代码变更做快照和撤销 | P2 |
| **MCP（Model Context Protocol）支持** | 无法接入外部数据源、数据库、文档系统等标准化接口 | P2 |
| **验证循环（Verify-before-completion）** | 改完代码后不自动编译/运行/测试，无法自证正确性 | P2 |
| **多 Agent 协调** | 单一 Loop 完成所有任务，无法拆分为 Planner + Executor + Reviewer 协作 | P2 |
| **浏览器/截图能力** | 无法验证 Web UI 效果，限制前端开发场景 | P3 |
| **会话恢复** | 进程退出后无法恢复之前的对话状态和未完成的任务 | P3 |

---

## 未来需求与路线图（头脑风暴整理）

基于上述差异，结合“核心 Agent Loop 内核”这一定位，我们整理出以下发展方向：

### 阶段一：让 Loop 能写代码（基础生存能力）
1. **文件工具集（Filesystem Tools）**
   - `read_file`：读取文件内容，支持行号范围、偏移量
   - `write_file`：创建新文件
   - `edit_file`：基于精确字符串替换的安全编辑
   - `search_files`：Grep 风格内容搜索
   - `list_directory`：目录 listing
2. **对话历史持久化**
   - 内存级 `Conversation` 对象，支持多轮 `chat()`
   - 可选的磁盘持久化（JSONL 格式）
3. **流式输出支持**
   - `LLMProvider` 增加流式接口，终端逐字打印
   - 同时支持流式中的 Tool Call 检测

### 阶段二：让 Loop 更可靠（工程化体验）
4. **计划模式（Plan Mode）**
   - 检测到复杂任务时，Agent 先输出计划，用户确认后再执行
   - 与 `InputInterceptor` 结合，支持 `/plan` 快捷指令
5. **并行 Tool Calls**
   - 一轮 LLM 响应中多个 tool call 并行执行，缩短总耗时
6. **上下文管理**
   -  token 计数估算与自动摘要（`/compact`）
   - 超长对话时自动丢弃早期非关键消息
7. **Git 集成工具**
   - `git_status`、`git_diff`、`git_commit`、`git_branch`
   - 自动在关键操作前查看 diff，防止误改

### 阶段三：让 Loop 更智能（进阶能力）
8. **Skill 系统完整化**
   - Skill 注册表 + 路由解析
   - 支持用户自定义 Skill（如 `/tdd`、`/review`）
9. **验证循环**
   - 代码修改后自动执行 `mvn compile` 或测试套件
   - 失败时将错误信息自动回传给 LLM 进行修复
10. **Checkpoint 与回滚**
    - 基于 Git 工作区或内存快照的变更回滚
11. **MCP Client 支持**
    - 接入外部 MCP Server，扩展工具边界

### 阶段四：生态与可扩展性
12. **多 Agent 协调**
    - 在现有 Hook 体系上，支持子 Agent / 专属 Loop 的委派
13. **会话恢复与状态管理**
    - 退出后重新进入可恢复对话和任务列表
14. **浏览器工具**
    - 基于 Playwright 或 Selenium 的截图与操作验证

---

## 设计理念

> **核心层永远精简，能力层无限扩展。**

`coloop-agent` 不是要成为第二个 Claude Code，而是要成为一个**透明、可理解、可魔改**的 Agent Loop 内核。当你想搞懂“一个 Coding Agent 到底是怎么工作的”，或者想“从零开始为自己的团队定制一个 Agent”，这里就是最好的起点。

---

## License

MIT
