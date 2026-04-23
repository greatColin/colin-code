# coloop-agent

A **lightweight, pluggable Java AGI agent core** — a minimal but powerful Agent Loop kernel for **Vibe Coding** and **Spec Coding** scenarios.

English | [简体中文](README.zh.md)

---

## Features

### 1. Core Agent Loop
- `AgentLoop.chat()`: a classic `while(true)` loop that calls the LLM → parses Tool Calls → executes tools → feeds results back to the LLM until a final text response is obtained.
- `AgentLoop.chatStream()`: SSE streaming mode that returns content word-by-word via `LLMProvider.StreamConsumer` callback. Tool calls are detected and accumulated during the stream.
- Supports a configurable maximum iteration limit (default 10) to prevent infinite loops.

### 2. Onion-Style Four-Layer Architecture
```
core/           ← Teaching skeleton: AgentLoop, interfaces, data models
capability/     ← Pluggable modules: Provider, Tool, PromptPlugin, Hook
runtime/        ← Dynamic assembly hub: CapabilityLoader, StandardCapability, AgentRuntime
entry/          ← Multiple entry points: MinimalDemo (tutorial), CliApp (full features)
```

### 3. Project Structure

Key packages and classes:

```
com.coloop.agent
├── core/                       ← Minimal kernel, never bloats
│   ├── agent/
│   │   ├── AgentLoop.java      ← Core while-loop: LLM → tool calls → result
│   │   └── AgentHook.java      ← Lifecycle hook interface
│   ├── message/
│   │   └── MessageBuilder.java ← Abstract message assembler interface
│   ├── prompt/
│   │   └── PromptPlugin.java   ← Abstract prompt generator interface
│   ├── provider/
│   │   ├── LLMProvider.java    ← LLM provider interface
│   │   ├── LLMResponse.java
│   │   └── ToolCallRequest.java
│   ├── tool/
│   │   ├── Tool.java           ← Tool contract
│   │   ├── BaseTool.java
│   │   └── ToolRegistry.java   ← Tool registration & dispatch
│   ├── command/                ← Command system core interfaces
│   │   ├── Command.java
│   │   ├── CommandRegistry.java
│   │   ├── CommandContext.java
│   │   ├── CommandResult.java
│   │   └── CommandExitException.java
│   └── interceptor/
│       └── InputInterceptor.java ← Pre-LLM input shortcut interceptor
├── capability/                 ← Pluggable implementations
│   ├── message/
│   │   └── StandardMessageBuilder.java ← OpenAI-format message builder
│   ├── prompt/
│   │   ├── PromptSegment.java        ← System prompt segment enum
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
│   ├── hook/
│   │   └── LoggingHook.java
│   └── command/                ← Command system implementations
│       ├── CommandInterceptor.java     ← InputInterceptor implementation
│       ├── CommandScanner.java         ← User-defined command directory scanner
│       ├── ExitCommand.java
│       ├── NewSessionCommand.java
│       ├── CompactCommand.java
│       ├── ModelCommand.java
│       └── HelpCommand.java
├── runtime/                    ← Assembly hub
│   ├── CapabilityLoader.java   ← Fluent chain builder
│   ├── StandardCapability.java ← Built-in capability catalog
│   ├── AgentRuntime.java       ← Runnable agent wrapper
│   └── config/
│       └── AppConfig.java
└── entry/                      ← Entry points
    ├── MinimalDemo.java        ← Tutorial mode (mock provider)
    └── CliApp.java             ← Full mode (real API)
```

### 4. Chain-Based Capability Assembly
Assemble agents flexibly via the `CapabilityLoader` fluent API:
```java
new CapabilityLoader()
    .withCapability(StandardCapability.EXEC_TOOL, config)
    .withCapability(StandardCapability.BASE_PROMPT, config)
    .withCapability(StandardCapability.LOGGING_HOOK, config)
    .build(provider, config);
```

### 4. Built-in Capabilities
| Capability | Description |
|------------|-------------|
| **ExecTool** | Shell command execution with timeout; cross-platform (Windows / Linux) |
| **BasePromptPlugin** | Injects basic system prompts (identity, time, working directory, OS) |
| **SkillPromptPlugin** | Scans and injects available skill descriptions into the system prompt |
| **AgentsMdPromptPlugin** | Auto-reads `AGENTS.md` from the working directory and injects it |
| **LoggingHook** | Prints debug logs at key Agent Loop lifecycle nodes |
| **Streaming (Backend)** | `LLMProvider.chatStream()` interface with SSE word-by-word streaming; `OpenAICompatibleProvider` implements true SSE; tool calls detected and accumulated during the stream |
| **Command System** | Dynamic `Command` interface + `CommandRegistry`; built-in `/exit`, `/new`, `/compact`, `/model`, `/help`; user-defined command scanning from `~/.coloop/commands/` and `./.coloop/commands/` (project-local overrides user-defined) |

### 5. Input Interceptor (`InputInterceptor`)
Intercepts user input before the LLM call. Useful for shortcuts (e.g. `/compact`), skill routing, permission checks, and other direct-return features.

### 6. Provider Support
- **MockProvider**: Pre-defined response sequence for tutorials, testing, and offline environments.
- **OpenAICompatibleProvider**: Supports any OpenAI-compatible API (e.g. OpenRouter, self-hosted vLLM).

### 7. Configuration Center
`AppConfig` loads from environment variables, preferring `COLIN_CODE_*` prefixes and falling back to `OPENAI_*`:
- `COLIN_CODE_OPENAI_MODEL`
- `COLIN_CODE_OPENAI_API_KEY`
- `COLIN_CODE_OPENAI_API_BASE`

---

## Quick Start

```bash
# Compile
mvn compile

# Run tutorial demo (mock mode, no API key required)
mvn compile exec:java -Dexec.mainClass="com.coloop.agent.entry.MinimalDemo"

# Run with a real API (set environment variables first)
export COLIN_CODE_OPENAI_API_KEY="sk-..."
export COLIN_CODE_OPENAI_API_BASE="https://api.openai.com/v1"
export COLIN_CODE_OPENAI_MODEL="gpt-4o"
mvn compile exec:java -Dexec.mainClass="com.coloop.agent.entry.CliApp"
```

---

## Core Differentiation (Agent Loop Kernel Focus)

Compared to mature tools like Claude Code, Aider, Cline, and Codex CLI, `coloop-agent` **only focuses on the core loop**. The following gaps and strengths are framed around Vibe Coding / Spec Coding experience.

### What We Already Have (Strengths)
| Feature | Description |
|---------|-------------|
| **Minimal Kernel** | No IDE dependency, no heavy frameworks, small codebase — ideal for learning and hacking |
| **Pure Java Ecosystem** | Friendly to Java developers; easy to integrate into enterprise Java environments |
| **Clear Plugin Boundaries** | `Tool` / `PromptPlugin` / `AgentHook` / `InputInterceptor` interfaces are explicit; extensions do not invade the core |
| **Environment-Aware Prompts** | BasePrompt auto-injects time, OS, and working directory to reduce LLM hallucinations |
| **Streaming Ready** | Backend SSE streaming fully implemented (`chatStream()` + `StreamConsumer`); frontend Markdown rendering, code highlighting, and `<think>` tag extraction delivered |

### What We Are Missing (High Value for Vibe / Spec Coding)

#### Backend / Core Loop
| Missing Capability | Impact | Priority |
|--------------------|--------|----------|
| **Filesystem Tools** | ✅ Done: `read_file`, `write_file`, `edit_file`, `search_files`, `list_directory` | P0 |
| **Conversation History Persistence** | ✅ Done: `AgentLoop` maintains message list across `chat()` calls | P0 |
| **Streaming Output (Backend)** | ✅ Done: `LLMProvider.chatStream()` + `OpenAICompatibleProvider` SSE word-by-word | P0 |
| **Plan Mode** | No way for the agent to draft a plan, get user confirmation, then execute | P1 |
| **Parallel Tool Calls** | OpenAI API supports multiple tool calls per turn, but we execute them serially | P1 |
| **Context Compression / Sliding Window** | Long sessions bloat the message list and eventually exceed context limits | P1 |
| **Git Integration** | Cannot auto-check diff, status, generate commit messages, or create branches | P1 |
| **Checkpoint / Rollback** | No snapshot-and-revert of code changes like Aider | P2 |
| **MCP (Model Context Protocol) Support** | Cannot connect to external data sources, databases, or document systems | P2 |
| **Verify-Before-Completion Loop** | Does not auto-compile / run / test after code changes to self-verify correctness | P2 |
| **Multi-Agent Coordination** | Single loop handles everything; no Planner + Executor + Reviewer collaboration | P2 |
| **Browser / Screenshot Capability** | Cannot validate Web UI effects, limiting frontend dev scenarios | P3 |
| **Session Recovery** | Cannot restore previous conversation state or pending tasks after process exit | P3 |

#### Frontend / Web UI
| Missing Capability | Impact | Priority |
|--------------------|--------|----------|
| **Streaming Output (Frontend)** | Backend supports SSE streaming, but `AgentService` still uses sync `chat()`; UI renders full response at once | P0 |
| **Markdown Rendering** | ✅ Done: `marked.js` integration renders bold, lists, links, tables, code blocks; `<think>` tags extracted into collapsible cards | P0 |
| **Code Syntax Highlighting** | ✅ Done: `highlight.js` integration with theme-aware styling across all 9 themes | P0 |
| **Command System** | ✅ Done: Dynamic `Command` interface + `CommandRegistry`; built-in `/exit`, `/new`, `/compact`, `/model`, `/help`; user-defined command scanning from `~/.coloop/commands/` and `./.coloop/commands/` | P1 |
| **Slash Command Autocomplete** | ✅ Done: Backend pushes available command list on WebSocket connect; frontend pops up fuzzy-matched command palette with descriptions; keyboard navigation supported | P1 |
| **Session History Sidebar** | Only one in-memory session exists; refreshing the page loses everything; no localStorage persistence | P1 |
| **Model Switching** | `AppConfig` supports multiple models, but users cannot switch at runtime from the UI | P1 |
| **Message Actions** | No copy, regenerate, or edit-message capabilities on chat bubbles | P1 |
| **Settings Panel** | No UI for temperature, max_tokens, font size, or stream toggle | P2 |
| **Welcome / Empty State** | New sessions show a blank chat area; no intro or quick-start examples | P2 |
| **Tool Result Visualization** | File reads, edits, and search results are plain text; no diff view, line numbers, or match highlighting | P2 |
| **Skill Execution Framework** | `SkillPromptPlugin` only injects descriptions; no real `/skill` routing or argument parsing | P2 |
| **Export / Share** | Cannot save a conversation as Markdown or generate a share link | P3 |
| **In-Conversation Search** | No Cmd+F to search within the current chat | P3 |
| **Multimodal Input** | Cannot upload images or files for the LLM to analyze | P3 |

---

## Roadmap

### Phase 1: Make the Loop Able to Write Code (Backend Foundation) ✅ Done
1. **Filesystem Tools**
   - ✅ `read_file`: read file contents with line-range / offset support
   - ✅ `write_file`: create new files (refuses to overwrite existing files)
   - ✅ `edit_file`: safe editing based on exact string replacement
   - ✅ `search_files`: regex content search with optional glob filtering
   - ✅ `list_directory`: directory listing
2. **Conversation History Persistence** ✅
   - `AgentLoop` maintains the message list internally, supporting multi-turn `chat()`
3. **Streaming Output (Backend)** ✅
   - `LLMProvider.chatStream()` interface with default fallback to synchronous `chat()`
   - `OpenAICompatibleProvider` implements true SSE word-by-word streaming
   - Detects and accumulates tool calls during the stream
4. **Web UI Foundation** ✅
   - WebSocket-based real-time chat interface
   - Collapsible cards for thinking, tool calls, and tool results
   - Theme system with 9 distinct themes + theme gallery
   - Auto-reconnect and connection status indicator

### Phase 2: Frontend Foundation + Command System (Current Focus)
5. **Command System Refactor** ✅ Done
   - ✅ Define `Command` interface + `CommandRegistry` for dynamic registration
   - ✅ Migrate hardcoded commands (`/new`, `/exit`) from `AgentService` and `AgentLoopThread` into the registry
   - ✅ Implement `/compact`, `/model`, and other built-in commands
   - ✅ Directory scanning for user-defined commands (`~/.coloop/commands/`) and project-local commands (`./.coloop/commands/`, overrides user-defined on name conflict)
   - ✅ Wire `CommandInterceptor` into `InputInterceptor` so `CapabilityLoader` can assemble it
   - ✅ 86 unit tests covering core interfaces, all command implementations, interceptor logic, scanner, and runtime integration
6. **Streaming Output (Frontend)**
   - Add `onStreamChunk()` to `AgentHook` interface for per-token streaming notifications
   - Switch `AgentService` from `agentLoop.chat()` to `agentLoop.chatStream()`
   - Extend `WebSocketLoggingHook` with `onStreamChunk()` to push SSE fragments via WebSocket (`type: stream_chunk`)
   - Frontend `chat.js`: append chunks in real time to a growing assistant message bubble, finalize on loop end
7. **Markdown Rendering + Code Highlighting** ✅ Done
   - ✅ Integrate `marked.js` for assistant message rendering
   - ✅ Integrate `highlight.js` for code block syntax highlighting
   - ✅ XSS sanitization with `DOMPurify`
   - ✅ `<think>` tag extraction into collapsible thinking cards
   - ✅ Theme-aware code block styling in all 9 themes
8. **Slash Command Autocomplete** ✅ Done
   - ✅ Backend pushes available command list on WebSocket connect
   - ✅ Frontend: typing `/` pops up a fuzzy-matched command palette with descriptions
   - ✅ Keyboard navigation (arrow keys, Enter, Esc)
9. **Model Switching**
   - Backend exposes available models via WebSocket on connect
   - Frontend dropdown next to the theme switcher
   - Switching rebuilds the session's `LLMProvider` with the new `ModelConfig`
10. **Message Actions**
    - Copy message to clipboard
    - Regenerate last assistant response
    - Edit a previous user message and re-run the loop

### Phase 3: Session Management + Backend Reliability
11. **Session History Sidebar**
    - localStorage / IndexedDB persistence for session metadata and messages
    - Left sidebar: session list with title, timestamp, and message count
    - New / delete / rename sessions; auto-title from first user message
    - Clicking a history item restores context (replays messages into `AgentLoop`)
12. **Plan Mode**
    - Agent outputs a plan first for complex tasks; user confirms before execution
    - Integrate with `InputInterceptor` to support `/plan` shortcut
13. **Parallel Tool Calls**
    - Execute multiple tool calls from a single LLM response in parallel to reduce latency
14. **Context Management**
    - Token count estimation and auto-summarization (`/compact`)
    - Automatically drop early non-critical messages in overly long conversations
15. **Git Integration Tools**
    - `git_status`, `git_diff`, `git_commit`, `git_branch`
    - Auto-check diff before critical operations to prevent accidental changes

### Phase 4: Frontend Polish + Advanced Capabilities
16. **Settings Panel**
    - Temperature, max_tokens, stream toggle
    - Font size and other UI preferences
    - Persisted in localStorage
17. **Welcome / Empty State**
    - Intro screen for new sessions with capability summary and example prompts
    - Quick-start buttons ("Analyze project structure", "Write a Hello World")
18. **Tool Result Visualization**
    - ReadFileTool: syntax-highlighted code with line numbers
    - EditFileTool: side-by-side diff view (red/green)
    - SearchFilesTool: highlighted match lines with clickable file paths
    - ExecTool: terminal-style output with exit-code coloring
19. **Complete Skill System**
    - Skill registry + routing parser
    - Support user-defined skills (e.g. `/tdd`, `/review`)
20. **Verification Loop**
    - Auto-run `mvn compile` or test suite after code changes
    - Feed errors back to the LLM for automatic fixing
21. **MCP Client Support**
    - Connect to external MCP servers to extend tool boundaries

### Phase 5: Ecosystem & Extensibility
22. **Checkpoint & Rollback**
    - Rollback changes based on Git workspace or in-memory snapshots
23. **Multi-Agent Coordination**
    - Support sub-agent / dedicated loop delegation on top of the existing Hook system
24. **Export / Share**
    - Export conversation as Markdown file
    - Shareable links (requires backend session persistence)
25. **Browser Tools**
    - Screenshot and interaction validation based on Playwright or Selenium
26. **Multimodal Input**
    - Image and file upload for vision-capable models

---

## Design Philosophy

> **Keep the core minimal; let capabilities expand infinitely.**

`coloop-agent` is not trying to become a second Claude Code. It aims to be a **transparent, understandable, and hackable** Agent Loop kernel. Whether you want to understand how a Coding Agent works under the hood, or build a tailored agent for your team from scratch, this is the best place to start.

---

## License

MIT
