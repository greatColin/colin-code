# Task List（任务管理）集成设计

## 背景

coloop-agent 作为一个可插拔的 Agent 底座，目前支持工具调用、生命周期钩子、Prompt 插件和命令拦截器。但在处理多步骤复杂任务时，LLM 缺乏一种结构化的方式来规划、跟踪和推进工作。

Claude Code 的任务列表系统证明：向 LLM 暴露一组任务管理工具，同时通过 PromptPlugin 将任务状态注入上下文、通过 AgentHook 向用户展示进度，可以显著提升 Agent 的自主规划能力和用户体验。

## 目标

1. **服务 LLM**：让 Agent 在执行复杂任务时能够自主创建任务列表、跟踪进度、管理依赖
2. **服务用户**：在控制台实时展示当前任务状态，让用户感知 Agent 的执行进度
3. **架构对齐**：完全复用现有洋葱架构（core/capability/runtime），零侵入核心循环
4. **可扩展**：当前内存存储，后续可无缝替换为文件/数据库存储

## 非目标

- 任务持久化（当前版本不做，但接口预留）
- 多 Agent 协作任务分配
- 任务优先级调度算法
- Web UI 任务展示（当前仅 CLI 控制台）

## 设计原则

1. **LLM 是任务的唯一写入者**：用户命令只读（查看），创建/更新/删除完全由 LLM 通过工具调用完成
2. **自动上下文注入优于显式读取**：通过 PromptPlugin 在每次 LLM 调用前注入当前任务状态，LLM 不需要主动调用 `task_list` 来获取进度
3. **返回极度简洁**：工具返回紧凑字符串，减少 token 消耗和上下文挤占
4. **已完成任务折叠**：PromptPlugin 注入时只显示 `IN_PROGRESS` + `PENDING`，`COMPLETED` 超过阈值后折叠
5. **单向顺序执行**：强制同时只有一个 `IN_PROGRESS` 任务，LLM 开始新任务时自动完成旧任务

---

## 数据模型

### TaskStatus

```java
enum TaskStatus {
    PENDING,      // 待执行
    IN_PROGRESS,  // 进行中（全局唯一）
    COMPLETED,    // 已完成
    DELETED       // 已删除（软删除）
}
```

### Task

```java
class Task {
    String id;                // 短 ID，如 "t-7a3f"
    String subject;           // 标题，祈使句
    String description;       // 详细描述
    TaskStatus status;
    List<String> blockedBy;   // 依赖的任务 ID 列表
    List<String> blocks;      // 阻塞的任务 ID 列表（双向维护）
    long createdAt;
    long updatedAt;
}
```

### 存储层接口（持久化扩展点）

```java
interface TaskStore {
    Task save(Task task);
    Task findById(String id);
    List<Task> findAll();
    void delete(String id);
}
```

默认实现 `InMemoryTaskStore`（基于 `ConcurrentHashMap`）。后续持久化只需新增 `JsonFileTaskStore` 等实现替换即可，`TaskService` 零改动。

---

## 业务层：TaskService

```java
class TaskService {
    private final TaskStore store;

    Task create(String subject, String description);
    Task get(String id);
    List<Task> list();
    Task update(String id, String subject, String description,
                TaskStatus status, List<String> blockedBy);
    void delete(String id);
}
```

### 约束逻辑

**1. 双向依赖维护**

当任务 B 设置 `blockedBy = [A]` 时：
- B.blockedBy 添加 A
- A.blocks 自动添加 B

当删除 A 时：
- 遍历 A.blocks，从每个被阻塞任务的 blockedBy 中移除 A

**2. 单 IN_PROGRESS 约束（自动完成旧任务）**

当 LLM 将某任务设为 `IN_PROGRESS` 时：
- 若已存在另一任务的 `status == IN_PROGRESS`，自动将其设为 `COMPLETED`
- 减少 LLM 的认知负担，模型只需要"开始新任务"

---

## Tool 层设计

4 个工具均继承 `BaseTool`，返回紧凑字符串。

### task_create

```json
{
  "name": "task_create",
  "description": "创建新任务。当用户请求涉及多个文件或步骤时，主动创建任务列表来跟踪进度。",
  "parameters": {
    "type": "object",
    "properties": {
      "subject": { "type": "string", "description": "任务标题，用祈使句" },
      "description": { "type": "string", "description": "任务详细描述" },
      "blocked_by": {
        "type": "array",
        "items": { "type": "string" },
        "description": "依赖的任务ID列表"
      }
    },
    "required": ["subject"]
  }
}
```

返回：`Created task t-7a3f: 读取配置文件 [PENDING]`

### task_list

```json
{
  "name": "task_list",
  "description": "列出所有任务及其当前状态。通常不需要主动调用，因为系统提示词已包含当前任务列表。",
  "parameters": {
    "type": "object",
    "properties": {}
  }
}
```

返回按状态分组的 Markdown 列表（无过滤参数）。

### task_get

```json
{
  "name": "task_get",
  "description": "获取单个任务的完整信息，包括依赖关系。",
  "parameters": {
    "type": "object",
    "properties": {
      "id": { "type": "string", "description": "任务ID" }
    },
    "required": ["id"]
  }
}
```

返回完整任务 JSON（含 blockedBy / blocks）。

### task_update

```json
{
  "name": "task_update",
  "description": "更新任务状态或信息。将任务设为 IN_PROGRESS 时，系统会自动完成之前进行中的任务。",
  "parameters": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "subject": { "type": "string" },
      "description": { "type": "string" },
      "status": { "enum": ["PENDING", "IN_PROGRESS", "COMPLETED", "DELETED"] },
      "blocked_by": {
        "type": "array",
        "items": { "type": "string" }
      }
    },
    "required": ["id"]
  }
}
```

返回：`Updated task t-7a3f: status=IN_PROGRESS`

---

## PromptPlugin：TaskStatusPromptPlugin

实现 `PromptPlugin` 接口，`priority = 5`（基础提示词之后，AGENTS.md 之前）。

职责：
1. **注入使用指南**（静态）：告诉 LLM 有哪些任务工具、何时使用、规则是什么
2. **注入当前任务状态**（动态）：每次 LLM 调用前生成最新列表

### 注入内容格式

```markdown
## 任务管理

你拥有 task_create / task_update / task_get / task_list 工具来跟踪多步骤工作。
规则：
1. 复杂请求主动创建任务
2. 同时只能有一个 IN_PROGRESS 任务
3. 用 blocked_by 表达步骤依赖

## 当前任务

- [IN_PROGRESS] t-7a3f 读取配置文件
- [PENDING] t-8b2c 解析字段
- [PENDING] t-9d1e 写入结果
...及 2 个已完成任务
```

### 已完成任务折叠

- 当 `COMPLETED` 任务数 <= 3 时，全部显示
- 当 `COMPLETED` 任务数 > 3 时，只显示最近 3 个，其余折叠为 `...及 N 个已完成任务`
- 若无任何任务，返回 `null`（不注入，节省 token）

---

## AgentHook：TaskDisplayHook

实现 `AgentHook` 接口，负责控制台实时渲染。

### 渲染时机

| 钩子 | 是否渲染 | 原因 |
|------|----------|------|
| `onLoopStart` | ❌ 否 | 此时可能还没有任务，渲染无意义 |
| `beforeLLMCall` | ❌ 否 | PromptPlugin 已注入，不需要重复显示 |
| `onToolCall(task_*)` | ✅ 是 | 任务变更后，打印一行紧凑日志 |
| `onLoopEnd` | ❌ 否 | 循环结束不需要任务更新 |

### 渲染风格

复用现有 `AnsiColors` 体系，与 `ClaudeCodeStyleLoggingHook` 风格保持一致：

```
[ TASK ] t-7a3f 读取配置文件 → IN_PROGRESS
```

单行输出，不画额外边框。颜色使用 `AnsiColors.TASK_COLOR`（新增，建议青色或灰色，区别于 TOOL 的黄色）。

---

## 命令层

利用现有 `CommandInterceptor` + `CommandRegistry`，新增 `/tasks` 命令。

```java
public class TasksCommand implements Command {
    public String getName() { return "tasks"; }

    public CommandResult execute(CommandContext ctx, String args) {
        // /tasks       → 显示全部任务（按状态分组）
        // /tasks <id>  → 显示单个任务详情
        return CommandResult.success(formattedOutput);
    }
}
```

**只读命令**。用户不能通过命令创建/更新/删除任务——这是 LLM 的专属职责。

---

## 集成方式

### 新增 StandardCapability

```java
TASK_MANAGEMENT(
    "task_management", "任务管理", "任务创建、更新、跟踪与展示",
    CapabilityType.COMPOSITE,
    config -> new TaskManagementCapability(config)
)
```

`TaskManagementCapability` 内部统一创建 `TaskService` 实例，然后拆包产出：
- 4 个 `Tool`（`task_create`, `task_list`, `task_get`, `task_update`）
- 1 个 `PromptPlugin`（`TaskStatusPromptPlugin`）
- 1 个 `AgentHook`（`TaskDisplayHook`）

### CapabilityLoader 扩展

`CapabilityType` 新增 `COMPOSITE` 类型，`CapabilityLoader.withCapability()` 支持拆包注册：

```java
case COMPOSITE:
    if (instance instanceof TaskManagementCapability) {
        TaskManagementCapability tmc = (TaskManagementCapability) instance;
        for (Tool tool : tmc.getTools()) withTool(tool);
        withPromptPlugin(tmc.getPromptPlugin());
        withHook(tmc.getHook());
    }
    break;
```

### 用户启用

在 `CliApp` 中增加一行：

```java
AgentLoop agentLoop = new CapabilityLoader()
    // ... 现有能力
    .withCapability(StandardCapability.TASK_MANAGEMENT, config)
    .build(provider, config);
```

---

## 文件结构

```
coloop-agent-core/src/main/java/com/coloop/agent/
├── core/task/                    # 核心接口与模型（新增包）
│   ├── Task.java
│   ├── TaskStatus.java
│   └── TaskStore.java
├── capability/task/              # 能力实现（新增包）
│   ├── InMemoryTaskStore.java
│   ├── TaskService.java
│   ├── TaskCreateTool.java
│   ├── TaskListTool.java
│   ├── TaskGetTool.java
│   ├── TaskUpdateTool.java
│   ├── TaskStatusPromptPlugin.java
│   ├── TaskDisplayHook.java
│   ├── TaskManagementCapability.java
│   └── command/
│       └── TasksCommand.java
└── runtime/
    ├── CapabilityType.java       # 新增 COMPOSITE
    ├── StandardCapability.java   # 新增 TASK_MANAGEMENT
    └── CapabilityLoader.java     # 支持 COMPOSITE 拆包
```

---

## 持久化路线图

当前版本：`InMemoryTaskStore`（纯内存，会话结束即丢失）。

未来版本只需：

1. 新增 `JsonFileTaskStore implements TaskStore`
2. 存储路径：`~/.coloop/tasks/{sessionId}.json`
3. 格式：JSON Lines 或单 JSON 数组
4. 在 `TaskManagementCapability` 中替换 `store = new JsonFileTaskStore(config)`

`TaskService` / `Tool` / `PromptPlugin` / `Hook` 全部零改动。

---

## 测试策略

1. **单元测试**：`TaskService` 的约束逻辑（双向依赖、单 IN_PROGRESS 自动完成）
2. **Tool 测试**：各工具的 execute 方法，验证返回格式和状态变更
3. **PromptPlugin 测试**：验证注入内容格式、已完成任务折叠逻辑
4. **集成测试**：通过 `CapabilityLoader` 完整组装，验证 LLM 调用链路中任务状态正确传递

---

## 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| LLM 不主动使用任务工具 | 功能闲置 | PromptPlugin 中的使用指南明确引导；工具描述包含"主动创建"指令 |
| 任务列表过长挤占上下文 | token 浪费 | 已完成任务折叠；无任务时不注入 |
| 用户手动命令与 LLM 工具冲突 | 数据不一致 | 命令层只读，禁止用户写入 |
| COMPOSITE 类型引入耦合 | CapabilityLoader 变复杂 | 拆包逻辑局限在 Loader 内，不影响其他组件 |
