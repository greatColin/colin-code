# Plan Mode Design Spec

## Overview

Plan Mode enables the agent to analyze a complex task, draft a structured execution plan, and wait for user confirmation before performing any write operations. This mirrors Claude Code's Shift+Tab behavior: the agent enters a read-only planning phase, outputs the plan, and only proceeds with execution after explicit user approval.

## Goals

- Agent can enter Plan Mode via `/plan <request>` command
- In Plan Mode, the agent uses only read/exploratory tools (read_file, search_files, list_directory, exec)
- Plan Mode runs in an isolated loop that does not pollute the main conversation history
- After the plan is generated, the agent pauses and waits for user confirmation
- Upon confirmation, the plan is injected into the main conversation context as execution guidance
- User can cancel a pending plan with `/cancel`

## Non-Goals

- Auto-detection of task complexity (manual trigger only)
- Plan Mode writing plan documents to disk (text-only output for this iteration)
- Multi-step plan execution with automatic progression (user confirms once, agent executes)

## Architecture

Plan Mode is implemented as a `CompositeCapability` in the `capability/plan/` package. It follows the project's onion architecture: all new code lives in the capability layer. The only core change is a minimal data field addition to `ConversationState` for shared state.

```
capability/plan/
├── PlanCapability.java          # CompositeCapability bundler
├── PlanCommand.java             # /plan command implementation
├── CancelCommand.java           # /cancel command implementation
├── PlanPromptPlugin.java        # Plan Mode system prompt
└── PlanInjectionHook.java       # Injects pending plan into main loop
```

One core class is extended:

```
core/context/ConversationState.java  # Adds pendingPlan field
```

## Components

### PlanCapability

Implements `CompositeCapability`. Bundles the PlanCommand, CancelCommand, PlanPromptPlugin, and PlanInjectionHook. Constructor receives `LLMProvider` and `AppConfig` because the PlanCommand needs to spawn an isolated Plan Loop sharing the same provider.

```java
public class PlanCapability implements CompositeCapability {
    public PlanCapability(LLMProvider provider, AppConfig config) { ... }
    public List<Tool> getTools() { return List.of(); }  // No tools directly; Plan Loop creates its own
    public PromptPlugin getPromptPlugin() { return planPromptPlugin; }
    public AgentHook getHook() { return planInjectionHook; }
    public PlanCommand getPlanCommand() { return planCommand; }
    public CancelCommand getCancelCommand() { return cancelCommand; }
}
```

### PlanCommand

Handles `/plan <request>`. Spawns a temporary read-only AgentLoop, runs the request through it, and stores the resulting plan in `ConversationState`.

**Responsibilities:**
- Parse request text from command arguments
- Build an isolated Plan Loop with read-only tools
- Execute the plan generation
- Store the plan in shared state
- Return the plan + confirmation prompt to the user

**Plan Loop tool set:**
- `ReadFileTool`
- `SearchFilesTool`
- `ListDirectoryTool`
- `ExecTool` (for exploratory commands like `git log`, `find`)

### PlanPromptPlugin

Injected into the Plan Loop's system prompt. Tells the LLM:
- You are in Plan Mode. Analyze the codebase and draft a step-by-step execution plan.
- You may only use read and exploratory tools. Do NOT write or edit files.
- Output a clear, numbered plan with file paths and expected changes.

### PlanInjectionHook

An `AgentHook` attached to the **main** AgentLoop. Monitors `beforeLLMCall`:
- If `ConversationState.getPendingPlan()` is not null, inject the plan into the message list
- Clears `pendingPlan` after injection (one-shot)
- Plan is injected as a user message before the current user input to provide execution context

### CancelCommand

Handles `/cancel`. Clears `pendingPlan` from `ConversationState` if present.

### ConversationState Extension

Adds two fields:
- `String pendingPlan` — the approved plan waiting for execution
- `String planRequest` — the original user request (preserved for context)

## Data Flow

### 1. Trigger Plan Mode

```
User: /plan implement user authentication
↓
CommandInterceptor intercepts → PlanCommand.execute(ctx, "implement user authentication")
↓
PlanCommand:
  1. createPlanLoop()          // Build isolated read-only loop
  2. planLoop.chat(request)    // Generate plan
  3. savePlanToState(plan)     // ConversationState.setPendingPlan(plan)
  4. return plan + "\n\nExecute? (y/yes to confirm, /cancel to abort)"
```

### 2. User Confirms

```
User: y
↓
AgentLoop.chat("y"):
  prepareMessages → messages add user:"y"
  beforeLLMCall → PlanInjectionHook detects pendingPlan
    1. injectPlanIntoMessages(messages, pendingPlan)
    2. ConversationState.clearPendingPlan()
  LLM call with full tools → normal execution with plan as context
```

### 3. User Cancels

```
User: /cancel
↓
CommandInterceptor → CancelCommand.execute(ctx, "")
  ConversationState.clearPendingPlan()
  return "Plan cancelled."
```

## Plan Injection Strategy

In `PlanInjectionHook.beforeLLMCall(messages)`:

1. Locate the last user message (the confirmation input like "y")
2. Remove it temporarily
3. Insert a plan context message: `{"role":"user","content":"Approved plan:\n\n{plan}\n\nProceed with execution."}`
4. Re-append the original user confirmation message

This ensures the LLM sees the full plan before the user's brief confirmation.

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Plan Loop throws exception | Return error message to user, do NOT set pendingPlan |
| Plan Loop reaches max iterations | Return partial plan + warning, still allow user to confirm |
| User confirms but plan was cleared | Normal chat continues, plan is ignored |
| User sends `/plan` while another plan is pending | New plan overwrites old plan |
| User sends another `/` command while plan is pending (e.g. `/help`) | Command is executed normally; pending plan remains unaffected |
| Plan Loop tool execution fails | Error is returned as tool result to Plan Loop; LLM may retry or adapt |

## Testing Strategy

| Test | Scope |
|------|-------|
| PlanCommand creates Plan Loop with only read tools | Unit test |
| PlanCommand stores plan in ConversationState | Unit test |
| PlanInjectionHook injects plan before user message | Unit test |
| PlanInjectionHook clears pendingPlan after injection | Unit test |
| CancelCommand clears pendingPlan | Unit test |
| Full flow: /plan → confirm → plan injected → execute | Integration test |
| /plan → /cancel → no injection on next message | Integration test |

## Integration Points

### AgentService

```java
PlanCapability planCap = new PlanCapability(provider, config);
cmdRegistry.register(planCap.getPlanCommand());
cmdRegistry.register(planCap.getCancelCommand());

agentLoop = new CapabilityLoader()
    // ... existing capabilities ...
    .withComposite(planCap)
    .withHook(hook)
    .withInterceptor(cmdInterceptor)
    .build(provider, config);
```

### CliApp

Same registration pattern as AgentService.

## Open Questions / Future Work

- **Plan document persistence**: Future iteration may allow Plan Mode to write the plan to `.cloop/plan.md` for reference
- **Multi-step confirmation**: Future iteration may support confirming/rejecting individual plan steps
- **Plan Mode auto-trigger**: Future iteration may prompt the user to enter Plan Mode when the LLM detects high complexity
