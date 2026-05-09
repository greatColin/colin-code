# coloop-agent

A **lightweight, pluggable Java AGI agent platform** — a minimal but powerful Agent Loop kernel for **Vibe Coding** and **Spec Coding** scenarios, plus a Spring Boot Web UI and an independent voice-input service.

English | [简体中文](README.zh.md)

---

## Repository Layout

`coloop-agent` is a Maven multi-module project plus an independent Python service:

```
coloop-agent/
├── coloop-agent-core/    ← Java agent kernel: core/ + capability/ + runtime/ + entry/
├── coloop-agent-server/  ← Spring Boot WebSocket server + Web UI (depends on core)
├── coloop-agent-voice/   ← Independent Python voice-input service (faster-whisper)
├── docs/                 ← Design specs, implementation plans, references
└── pom.xml               ← Parent POM (Java 17, Spring Boot 3.2.5)
```

The Java modules share `coloop-agent-setting.json` (located in `coloop-agent-core/src/main/resources/`) for model, MCP, and voice configuration.

---

## Features

### 1. Core Agent Loop
- `AgentLoop.chat()`: a classic `while(true)` loop that calls the LLM → parses Tool Calls → executes tools → feeds results back to the LLM until a final text response is obtained.
- `AgentLoop.chatStream()`: SSE streaming mode that returns content word-by-word via `LLMProvider.StreamConsumer` callback. Tool calls are detected and accumulated during the stream.
- `AgentLoopThread`: thread-wrapped loop used by the server for interruptible runs (`/stop` command).
- Configurable maximum iteration limit (default 50) to prevent infinite loops.

### 2. Onion-Style Four-Layer Architecture
```
core/           ← Teaching skeleton: AgentLoop, interfaces, data models, ConversationState
capability/     ← Pluggable modules: Provider, Tool, PromptPlugin, Hook, Command, MCP, Subagent, Task, Plan, History
runtime/        ← Dynamic assembly hub: CapabilityLoader, StandardCapability, CompositeCapability, AgentRuntime
entry/          ← Multiple entry points: MinimalDemo (tutorial), CliApp (full features)
```

### 3. Project Structure

Key packages and classes inside `coloop-agent-core`:

```
com.coloop.agent
├── core/                                ← Minimal kernel, never bloats
│   ├── agent/
│   │   ├── AgentLoop.java               ← Core while-loop: LLM → tool calls → result
│   │   ├── AgentLoopThread.java         ← Interruptible loop runner used by the server
│   │   └── AgentHook.java               ← Lifecycle hook interface (incl. onStreamChunk, onSubagent*)
│   ├── context/
│   │   ├── ContextCompactor.java        ← Context compression strategy interface
│   │   ├── ConversationSummary.java     ← Summary data object
│   │   ├── ConversationState.java       ← Cross-component session state (incl. SubagentRegistry, plan)
│   │   └── PlanTask.java                ← Plan-mode task record
│   ├── history/                         ← NEW: conversation persistence primitives
│   │   ├── HistoryMessage.java
│   │   ├── SessionMeta.java
│   │   ├── ConversationHistoryStore.java
│   │   ├── FileSystemHistoryStore.java
│   │   └── HistoryRecordingHook.java
│   ├── command/                         ← Command system core interfaces
│   │   ├── Command.java / CommandRegistry.java / CommandContext.java
│   │   ├── CommandResult.java / CommandExitException.java
│   ├── interceptor/InputInterceptor.java
│   ├── message/MessageBuilder.java
│   ├── prompt/PromptPlugin.java
│   ├── provider/{LLMProvider, LLMResponse, ToolCallRequest}.java
│   ├── task/{Task, TaskStatus, TaskStore}.java   ← NEW: task data model
│   ├── tool/{Tool, BaseTool, ToolRegistry}.java
│   └── util/TokenEstimator.java
├── capability/                          ← Pluggable implementations
│   ├── command/                         ← Built-in commands + scanner
│   │   ├── ExitCommand / NewSessionCommand / CompactCommand
│   │   ├── ModelCommand / HelpCommand / StopCommand
│   │   ├── CommandInterceptor / CommandScanner
│   │   ├── MdCommandParser / MdPromptCommand / MdCommandDefinition
│   ├── context/LLMContextCompactor.java
│   ├── hook/{LoggingHook, ClaudeCodeStyleLoggingHook, AnsiColors}.java
│   ├── mcp/                             ← MCP client (STDIO + JSON-RPC)
│   │   ├── McpCapability / McpClient / McpTransport
│   │   ├── McpToolAdapter / McpToolDefinition
│   │   └── JsonRpcRequest / JsonRpcResponse / McpException
│   ├── message/StandardMessageBuilder.java
│   ├── plan/                            ← /plan mode
│   │   ├── PlanCapability / PlanCommand / CancelCommand
│   │   ├── PlanInjectionHook / PlanPromptPlugin
│   ├── prompt/{Base, AgentsMd, Skill, Summary}PromptPlugin + PromptSegment
│   ├── provider/{openai/OpenAICompatibleProvider, mock/MockProvider}
│   ├── subagent/                        ← NEW: named multi-agent system
│   │   ├── SubagentInstance / SubagentRegistry / SubagentEventListener
│   │   ├── SubagentLoopFactory / SubagentPromptPlugin
│   │   ├── AgentTool / SendMessageTool / ListModelsTool
│   │   └── SubagentManagementCapability
│   ├── task/                            ← NEW: task management capability
│   │   ├── TaskCreateTool / TaskUpdateTool / TaskGetTool / TaskListTool
│   │   ├── TaskService / InMemoryTaskStore
│   │   ├── TaskStatusPromptPlugin / TaskDisplayHook
│   │   ├── TaskManagementCapability
│   │   └── command/TasksCommand
│   └── tool/
│       ├── exec/ExecTool.java
│       └── filesystem/{Read, Write, Edit, Search, ListDirectory}FileTool
├── runtime/
│   ├── CapabilityLoader.java            ← Fluent chain builder, supports snapshotTools()
│   ├── CompositeCapability.java         ← Bundles multiple capabilities under one entry
│   ├── StandardCapability.java          ← Built-in capability catalog
│   ├── runtime/{AgentRuntime, LoopInputAgentRuntime}.java
│   └── config/AppConfig.java            ← Loads coloop-agent-setting.json (with JSON comments)
└── entry/
    ├── MinimalDemo.java                 ← Tutorial mode (mock provider)
    └── CliApp.java                      ← Full CLI mode (real API + commands + MCP)
```

Key packages inside `coloop-agent-server`:

```
com.coloop.agent.server
├── config/        ← Spring configuration
├── controller/    ← REST endpoints (e.g. /api/config)
├── dto/           ← WebSocketMessage with factory methods for all event types
├── hook/          ← AbstractWebSocketLoggingHook + WebSocketLoggingHook + SubagentLoggingHook
├── service/       ← AgentService: per-session AgentRuntime assembly
└── websocket/     ← AgentWebSocketHandler: chat / history / subagent / toast routing
```

Frontend (under `coloop-agent-server/src/main/resources/static/`): `index.html`, `chat.js`, `topology.js`, `group-chat.js`, `graph-state.js`, themes/, plus `theme-gallery.html` for previewing all 9 themes.

### 4. Chain-Based Capability Assembly
Assemble agents flexibly via the `CapabilityLoader` fluent API:
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

### 5. Built-in Capabilities
| Capability | Description |
|------------|-------------|
| **ExecTool** | Shell command execution with timeout; cross-platform (Windows / Linux) |
| **Filesystem Tools** | `read_file`, `write_file`, `edit_file`, `search_files`, `list_directory` (under `capability/tool/filesystem`) |
| **BasePromptPlugin** | Injects basic system prompts (identity, time, working directory, OS) |
| **SkillPromptPlugin** | Scans and injects available skill descriptions into the system prompt |
| **AgentsMdPromptPlugin** | Auto-reads `AGENTS.md` from the working directory and injects it |
| **LoggingHook / ClaudeCodeStyleLoggingHook** | Prints debug logs at key Agent Loop lifecycle nodes; Claude-Code-style formatting available |
| **Streaming Output** | `LLMProvider.chatStream()` + `OpenAICompatibleProvider` true SSE; tool calls detected and accumulated mid-stream |
| **Context Compression** | `/compact` compresses history into a summary injected into system prompt; auto-compact at 80% token threshold; per-model `maxContextSize` (e.g. minimax 200k, glm 100k) |
| **Command System** | Dynamic `Command` interface + `CommandRegistry`; built-in `/exit`, `/new`, `/compact`, `/model`, `/help`, `/plan`, `/cancel`, `/stop`, `/tasks`; user-defined commands scanned from `~/.coloop/commands/` and `./.coloop/commands/` (JSON for static/shell, **Markdown** for prompt templates with `$ARGUMENTS`; project-local overrides user-level) |
| **Plan Mode** | `/plan` enters read-only planning mode with isolated AgentLoop using read/search/list/exec tools; plan stored in `ConversationState`; injected into main loop on confirmation; `/cancel` aborts |
| **MCP Client** | `McpClient` connects to MCP servers via STDIO + JSON-RPC; `McpCapability` registers remote tools as local tools, configured via `AppConfig.mcpServers` |
| **Subagent System (NEW)** | Named multi-agent coordination: `AgentTool` creates/replaces a subagent, `SendMessageTool` appends messages to an existing subagent, `ListModelsTool` queries available models. `SubagentRegistry` tracks lifecycle, `SubagentLoggingHook` routes per-agent events to the WebSocket. Subagents may pick a different `model` per-agent, with fallback toast on unknown keys. |
| **Task Management (NEW)** | `TaskCreateTool / TaskUpdateTool / TaskGetTool / TaskListTool` plus `TaskStatusPromptPlugin` and `TaskDisplayHook`; only triggered when ≥3 steps; restricted to the main agent; `/tasks` command for CLI/Web display |
| **History Persistence (NEW)** | `ConversationHistoryStore` + `FileSystemHistoryStore` writes session metadata and messages to disk; `HistoryRecordingHook` captures lifecycle events; the Web UI sidebar lists past sessions and restores context on click |

### 6. Input Interceptor (`InputInterceptor`)
Intercepts user input before the LLM call. Drives the `/`-command shortcut, plan mode, skill routing, permission checks, and other direct-return features.

### 7. Provider Support
- **MockProvider**: Pre-defined response sequence for tutorials, testing, and offline environments.
- **OpenAICompatibleProvider**: Supports any OpenAI-compatible API (e.g. OpenRouter, Minimax, GLM, self-hosted vLLM) with full SSE streaming.

### 8. Configuration Center
`AppConfig` loads from `coloop-agent-core/src/main/resources/coloop-agent-setting.json` (with `${VAR}` env-var interpolation) and supports **JSON line and block comments** (`//` and `/* */`). Layout:

```jsonc
{
  // Global default model — used when a subagent does not specify a model
  "defaultModel": "minimax",
  "maxIterations": 50,
  "execTimeoutSeconds": 30,

  "models": {
    "minimax": {
      "description": "Main model — strong reasoning, good for complex tasks",
      "apiKey": "${COLIN_CODE_MINIMAX_API_KEY}",
      "apiBase": "https://api.minimaxi.com/v1",
      "model": "MiniMax-M2.7",
      "maxContextSize": "200k"
    },
    "glm-4-free": {
      "description": "Free lightweight model — good for simple tasks",
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

`maxContextSize` accepts unit suffixes (`100k`, `200k`, `1m`) or raw numbers.

---

## Quick Start

### Build everything

```bash
mvn clean install -DskipTests
```

### Run the tutorial demo (no API key required)

```bash
cd coloop-agent-core
mvn exec:java -Dexec.mainClass="com.coloop.agent.entry.MinimalDemo"
```

### Run the CLI app (real API)

```bash
export COLIN_CODE_MINIMAX_API_KEY="sk-..."
# or COLIN_CODE_OPENAI_API_KEY / COLIN_CODE_GLM_API_KEY etc.
cd coloop-agent-core
mvn exec:java -Dexec.mainClass="com.coloop.agent.entry.CliApp"
```

### Run the Web UI server

```bash
cd coloop-agent-server
mvn spring-boot:run
# Then open http://localhost:8080/ in a browser
```

### Run the voice-input service (optional)

```bash
cd coloop-agent-voice
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
python main.py
# Then open http://localhost:8000/static/voice-input.html
```

The voice service streams transcribed text to the agent server via the WebSocket URL configured in `voice.coloopServer.wsUrl`.

---

## Feature Overview

Features grouped by capability domain, ordered roughly from foundational kernel features to higher-level UX. Status: ✅ implemented · ⚠️ partial · ⏳ planned.

### 1. Agent Loop Core
| Feature | Status | Notes |
|---------|:------:|-------|
| Synchronous chat loop | ✅ | `AgentLoop.chat()` — LLM → parse Tool Calls → execute → feed results back |
| Streaming chat | ✅ | `chatStream()` SSE word-by-word + mid-stream tool-call accumulation |
| Interruptible execution | ✅ | `AgentLoopThread` + `/stop` command |
| Max-iteration cap | ✅ | Default 50 turns, configurable |
| Parallel tool calls | ⏳ | OpenAI returns multiple tool calls per turn; currently executed serially |
| Verify-before-completion | ⏳ | Auto-compile / run / test after code changes, feed errors back |

### 2. Providers & Model Management
| Feature | Status | Notes |
|---------|:------:|-------|
| MockProvider | ✅ | Pre-defined response sequences for tutorials and offline tests |
| OpenAI-compatible Provider | ✅ | Any OpenAI-compatible API + true SSE streaming |
| Multi-model configuration | ✅ | `models.*` + global `defaultModel` |
| Runtime model switching | ✅ | `/model` rebuilds the session's `LLMProvider` |
| Per-subagent model | ✅ | `AgentTool` accepts `model` param; toast notifies on unknown key (fallback to default) |
| Model listing tool | ✅ | `ListModelsTool` exposes configured models to the LLM |
| Model `description` metadata | ✅ | Surfaced in UI and `ListModelsTool` |

### 3. Tools & External Integration
| Feature | Status | Notes |
|---------|:------:|-------|
| Shell execution | ✅ | `ExecTool`, cross-platform, configurable timeout |
| File read | ✅ | `read_file` with line-range / offset |
| File write (create only) | ✅ | `write_file` refuses to overwrite |
| File edit | ✅ | `edit_file` exact-string replacement |
| File search | ✅ | `search_files` regex + glob filter |
| Directory listing | ✅ | `list_directory` |
| MCP client | ✅ | STDIO + JSON-RPC, auto-registers remote tools as local |
| Tool-result diff / syntax view | ⏳ | Currently shown as plain text |
| Browser / screenshot tools | ⏳ | Playwright / Selenium integration |

### 4. Prompt Engineering
| Feature | Status | Notes |
|---------|:------:|-------|
| Environment-aware base prompt | ✅ | Injects identity, time, OS, working directory |
| AGENTS.md auto-injection | ✅ | Reads project-local `AGENTS.md` |
| Compaction summary injection | ✅ | `SummaryPromptPlugin` writes summary into system prompt |
| Skill description injection | ⚠️ | Descriptions injected; no `/skill` routing yet |
| Task status injection | ✅ | `TaskStatusPromptPlugin` keeps the LLM in sync with todos |
| Plan injection | ✅ | `PlanPromptPlugin` writes confirmed plan into the main loop |
| Subagent isolated prompt | ✅ | `SubagentPromptPlugin` |

### 5. Commands & Input Routing
| Feature | Status | Notes |
|---------|:------:|-------|
| `Command` interface + `CommandRegistry` | ✅ | Dynamic registration |
| Built-in commands | ✅ | `/exit`, `/new`, `/compact`, `/model`, `/help`, `/plan`, `/cancel`, `/stop`, `/tasks` |
| User-level JSON commands | ✅ | `~/.coloop/commands/*.json` for static / shell commands |
| Markdown prompt commands | ✅ | YAML frontmatter + `$ARGUMENTS` interpolation |
| Project-local override | ✅ | `./.coloop/commands/` wins on name collision |
| Slash autocomplete | ✅ | Backend pushes command list; frontend fuzzy palette + keyboard nav |

### 6. Context Management
| Feature | Status | Notes |
|---------|:------:|-------|
| `TokenEstimator` | ✅ | ~1.5 chars/token for Chinese, ~0.25 for non-Chinese |
| Per-model `maxContextSize` | ✅ | Accepts `100k` / `200k` / `1m` / raw integer |
| Manual compaction | ✅ | `/compact` |
| Auto-compaction at 80% | ✅ | Keeps last 2 turns |
| Live context-usage display | ✅ | Per-session bar + per-subagent bars in the Web UI |

### 7. Plan Mode
| Feature | Status | Notes |
|---------|:------:|-------|
| Read-only planning loop | ✅ | `/plan` runs an isolated AgentLoop with read/search/list/exec tools only |
| Plan stored in state | ✅ | `PlanTask` data model in `ConversationState` |
| Injection into main loop | ✅ | `PlanInjectionHook` on user confirmation |
| Cancel pending plan | ✅ | `/cancel` |

### 8. Session History & Persistence
| Feature | Status | Notes |
|---------|:------:|-------|
| In-memory cross-turn history | ✅ | `AgentLoop` keeps the message list across `chat()` calls |
| File-backed session store | ✅ | `FileSystemHistoryStore` writes messages + metadata |
| History recording hook | ✅ | `HistoryRecordingHook` captures lifecycle events |
| Sidebar history list | ✅ | Accordion sidebar; click to restore |
| Subagent registry restoration | ✅ | Subagent state replays with the session |
| Context-usage persistence | ✅ | Survives page refresh |
| Markdown export | ⏳ | |
| Shareable links | ⏳ | Requires server-side sharing |
| In-conversation search | ⏳ | |

### 9. Multi-Agent Coordination
| Feature | Status | Notes |
|---------|:------:|-------|
| Subagent data model | ✅ | `SubagentInstance` |
| Thread-safe registry | ✅ | `SubagentRegistry` with createOrReplace / clear |
| Create / replace tool | ✅ | `AgentTool` |
| Continue-conversation tool | ✅ | `SendMessageTool` appends to existing subagent |
| Per-agent state isolation | ✅ | Independent message lists, context bars, hooks |
| Event routing | ✅ | `SubagentLoggingHook` pushes per-agent events to the WebSocket |
| Agent sidebar | ✅ | Lists running subagents and status |
| Topology canvas | ✅ | Nodes + edges + status labels |
| Group-chat mode | ✅ | Multi-agent conversation view |
| Combined Mode UI | ✅ | Switch between chat / topology / group chat |

### 10. Task Management
| Feature | Status | Notes |
|---------|:------:|-------|
| Task data model | ✅ | `Task` + `TaskStatus` |
| Task tools | ✅ | `TaskCreateTool` / `TaskUpdateTool` / `TaskGetTool` / `TaskListTool` |
| `/tasks` command | ✅ | CLI + Web display |
| Status prompt injection | ✅ | `TaskStatusPromptPlugin` |
| Display hook | ✅ | `TaskDisplayHook` writes task cards to the WebSocket |
| 3+ step threshold | ✅ | Tools only activate for multi-step work |
| Main-agent restriction | ✅ | Subagents do not manage tasks |

### 11. Web UI
| Feature | Status | Notes |
|---------|:------:|-------|
| WebSocket realtime chat | ✅ | Auto-reconnect + connection status indicator |
| Streaming incremental render | ✅ | Debounced (100 ms / 50 chars) + blinking cursor |
| Markdown rendering | ✅ | `marked.js` |
| Code syntax highlighting | ✅ | `highlight.js` |
| XSS sanitization | ✅ | `DOMPurify` |
| Thinking cards | ✅ | `<think>` tag extraction, collapsible |
| 9 themes + theme gallery | ✅ | All themes adapt code blocks and sidebar sections |
| Slash command palette | ✅ | Fuzzy match + keyboard navigation |
| History sidebar | ✅ | Accordion list, restore on click |
| Agent sidebar | ✅ | Subagent status |
| Topology / group-chat / combined mode | ✅ | Graph view canvas |
| Model fallback toast | ✅ | Shown when subagent model key is unknown |
| Task display cards | ✅ | Shared rendering with CLI |
| Message actions | ⏳ | Copy / regenerate / edit-and-rerun |
| Settings panel | ⏳ | Temperature, font size, stream toggle, etc. |
| Welcome / empty state | ⏳ | Intro + quick-start examples |
| Tool-result visualization | ⏳ | Diff / line numbers / terminal-styled exec output |
| Skill routing | ⏳ | Today only the description is injected |
| In-conversation search | ⏳ | Cmd+F over current chat |
| Multimodal upload | ⏳ | Image / file input |

### 12. Voice Input (independent Python service)
| Feature | Status | Notes |
|---------|:------:|-------|
| faster-whisper engine | ✅ | Multiple model sizes; CPU or CUDA |
| EnergyVAD | ✅ | RMS-based voice activity detection |
| Multiple recognition modes | ✅ | `realtime` / `realtime_final` / `final_only` |
| Strategy interfaces | ✅ | `TranscriptionStrategy` + `CorrectionStrategy` |
| `VoiceFactory` assembly | ✅ | Config-driven strategy creation |
| Streaming corrections | ✅ | Real-time fix-ups during recognition |
| LLM post-correction | ✅ | Reuses `coloop-agent-setting.json` model config |
| HTTP / WebSocket transcription adapters | ✅ | Pluggable backends beyond local Whisper |
| Liquid-glass UI | ✅ | One-tap mic + waveform visualization |
| Push to agent server | ✅ | Streams transcribed text via WebSocket |

### 13. Configuration
| Feature | Status | Notes |
|---------|:------:|-------|
| `${VAR}` env-var interpolation | ✅ | API keys, URLs, etc. |
| JSON line / block comments | ✅ | `//` and `/* */` |
| Model `description` field | ✅ | Surfaced via UI + `ListModelsTool` |
| Global `defaultModel` | ✅ | Used when subagent omits `model` |
| `maxContextSize` unit suffix | ✅ | `k` / `m` / raw integer |
| MCP server config | ✅ | command + args + env (`env` supports `${models.*.apiKey}`) |
| Voice module config | ✅ | Transcription/correction strategies, recognition mode, coloop WS URL |

---

## Design Philosophy

> **Keep the core minimal; let capabilities expand infinitely.**

`coloop-agent` is not trying to become a second Claude Code. It aims to be a **transparent, understandable, and hackable** Agent Loop platform. The kernel stays small enough to read in an afternoon; new capabilities (subagents, history, voice, MCP) plug into well-defined interfaces without modifying the core. Whether you want to understand how a Coding Agent works under the hood, or build a tailored agent for your team from scratch, this is the best place to start.

---

## License

MIT
