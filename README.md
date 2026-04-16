# coloop-agent

A **lightweight, pluggable Java AGI agent core** — a minimal but powerful Agent Loop kernel for **Vibe Coding** and **Spec Coding** scenarios.

English | [简体中文](README.zh.md)

---

## Features

### 1. Core Agent Loop
- `AgentLoop.chat()`: a classic `while(true)` loop that calls the LLM → parses Tool Calls → executes tools → feeds results back to the LLM until a final text response is obtained.
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
│   └── hook/
│       └── LoggingHook.java
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

### What We Are Missing (High Value for Vibe / Spec Coding)
| Missing Capability | Impact | Priority |
|--------------------|--------|----------|
| **Filesystem Tools** | ✅ Done: `read_file`, `write_file`, `edit_file`, `search_files`, `list_directory` | P0 |
| **Conversation History Persistence** | ✅ Done: `AgentLoop` maintains message list across `chat()` calls | P0 |
| **Streaming Output** | ✅ Done: `LLMProvider.chatStream()` + `OpenAICompatibleProvider` SSE word-by-word | P0 |
| **Plan Mode** | No way for the agent to draft a plan, get user confirmation, then execute | P1 |
| **Parallel Tool Calls** | OpenAI API supports multiple tool calls per turn, but we execute them serially | P1 |
| **Context Compression / Sliding Window** | Long sessions bloat the message list and eventually exceed context limits | P1 |
| **Skill Execution Framework** | `SkillPromptPlugin` only injects descriptions; no real `/skill` routing or argument parsing | P1 |
| **Git Integration** | Cannot auto-check diff, status, generate commit messages, or create branches | P1 |
| **Checkpoint / Rollback** | No snapshot-and-revert of code changes like Aider | P2 |
| **MCP (Model Context Protocol) Support** | Cannot connect to external data sources, databases, or document systems | P2 |
| **Verify-Before-Completion Loop** | Does not auto-compile / run / test after code changes to self-verify correctness | P2 |
| **Multi-Agent Coordination** | Single loop handles everything; no Planner + Executor + Reviewer collaboration | P2 |
| **Browser / Screenshot Capability** | Cannot validate Web UI effects, limiting frontend dev scenarios | P3 |
| **Session Recovery** | Cannot restore previous conversation state or pending tasks after process exit | P3 |

---

## Roadmap (Brainstormed)

### Phase 1: Make the Loop Able to Write Code (Basic Survival) ✅ Done
1. **Filesystem Tools**
   - ✅ `read_file`: read file contents with line-range / offset support
   - ✅ `write_file`: create new files (refuses to overwrite existing files)
   - ✅ `edit_file`: safe editing based on exact string replacement
   - ✅ `search_files`: regex content search with optional glob filtering
   - ✅ `list_directory`: directory listing
2. **Conversation History Persistence** ✅
   - `AgentLoop` maintains the message list internally, supporting multi-turn `chat()`
3. **Streaming Output Support** ✅
   - `LLMProvider.chatStream()` interface with default fallback to synchronous `chat()`
   - `OpenAICompatibleProvider` implements true SSE word-by-word streaming
   - Detects and accumulates tool calls during the stream

### Phase 2: Make the Loop Reliable (Engineering Experience)
4. **Plan Mode**
   - Agent outputs a plan first for complex tasks; user confirms before execution
   - Integrate with `InputInterceptor` to support `/plan` shortcut
5. **Parallel Tool Calls**
   - Execute multiple tool calls from a single LLM response in parallel to reduce latency
6. **Context Management**
   - Token count estimation and auto-summarization (`/compact`)
   - Automatically drop early non-critical messages in overly long conversations
7. **Git Integration Tools**
   - `git_status`, `git_diff`, `git_commit`, `git_branch`
   - Auto-check diff before critical operations to prevent accidental changes

### Phase 3: Make the Loop Smarter (Advanced Capabilities)
8. **Complete Skill System**
   - Skill registry + routing parser
   - Support user-defined skills (e.g. `/tdd`, `/review`)
9. **Verification Loop**
   - Auto-run `mvn compile` or test suite after code changes
   - Feed errors back to the LLM for automatic fixing
10. **Checkpoint & Rollback**
    - Rollback changes based on Git workspace or in-memory snapshots
11. **MCP Client Support**
    - Connect to external MCP servers to extend tool boundaries

### Phase 4: Ecosystem & Extensibility
12. **Multi-Agent Coordination**
    - Support sub-agent / dedicated loop delegation on top of the existing Hook system
13. **Session Recovery & State Management**
    - Resume conversations and task lists after re-entering the app
14. **Browser Tools**
    - Screenshot and interaction validation based on Playwright or Selenium

---

## Design Philosophy

> **Keep the core minimal; let capabilities expand infinitely.**

`coloop-agent` is not trying to become a second Claude Code. It aims to be a **transparent, understandable, and hackable** Agent Loop kernel. Whether you want to understand how a Coding Agent works under the hood, or build a tailored agent for your team from scratch, this is the best place to start.

---

## License

MIT
