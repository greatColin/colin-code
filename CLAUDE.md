# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`coloop-agent` 是一个**可插拔、模块隔离的轻量级 AGI Agent 底座**，起源于最小可用的 agent-loop + exec 演示，目标演进为类似 Claude Code 的简化克隆版。项目基于 Maven 构建，使用 JDK 21，主要依赖 Jackson 和 OkHttp。

## 常用命令

- **编译并运行 Demo**：
  ```bash
  mvn compile exec:java -Dexec.mainClass="com.coloop.agent.entry.MinimalDemo"
  ```
- **编译**：`mvn compile`
- **打包**：`mvn package`
- **清理**：`mvn clean`

## 架构设计

项目采用**核心-插件洋葱架构**，分为四层：

```
core/           ← 教学核心，永远精简：AgentLoop、抽象接口、数据模型
capability/     ← 可插拔能力模块：Provider、Tool、PromptPlugin、Hook
runtime/        ← 动态组装中枢：CapabilityLoader、StandardCapability、AgentRuntime
entry/          ← 多入口：MinimalDemo（教学）、CliApp（完整功能）
```

### 核心流程

1. **教学入口** `entry.MinimalDemo`：只加载最基础的 mock provider + exec tool，代码极简，用于学习核心 loop。
2. **完整入口** `entry.CliApp`：通过 `runtime.CapabilityLoader` 链式组装所有能力，支持真实 API。

### 模块职责

#### core 层（教学骨架）
- **`core.agent.AgentLoop`**：Agent 核心循环。`while` 中调用 LLM，处理 `tool_calls`，触发 `AgentHook`。
- **`core.message.MessageBuilder`**：消息构建抽象接口。
- **`core.interceptor.InputInterceptor`**：输入拦截器。支持 `/compact`、Skill 等快捷指令直接短路返回。
- **`core.provider.LLMProvider`**：LLM 提供商接口。
- **`core.tool.Tool` / `ToolRegistry`**：工具接口和注册表。

#### capability 层（可插拔能力）
- **`capability.provider.openai` / `mock`**：具体 Provider 实现。
- **`capability.tool.exec`**：`ExecTool`，Shell 执行。
- **`capability.message`**：`StandardMessageBuilder`，OpenAI 格式的消息组装实现。
- **`capability.prompt`**：`BasePromptPlugin`、`SkillPromptPlugin`、`AgentsMdPromptPlugin` 等 `PromptPlugin` 实现，以及 `PromptSegment` 提示词段落定义。
- **`capability.hook`**：`LoggingHook` 等生命周期实现。

#### runtime 层（组装中枢）
- **`runtime.CapabilityLoader`**：链式组装 Agent。支持 `withCapability()`、`withTool()`、`withPromptPlugin()`、`withHook()`、`withInterceptor()`。
- **`runtime.StandardCapability`**：内置能力目录枚举（`exec`、`base_prompt`、`logging` 等），规范所有内置能力的 ID 和说明。
- **`runtime.AgentRuntime`**：组装完成后的可运行代理。
- **`runtime.config.AppConfig`**：配置中心（原 `DemoConfig` 升级）。

### 能力扩展方式

新增内置能力：
1. 在 `capability/` 下新建子包实现具体能力。
2. 如果是通用内置能力，将其注册到 `StandardCapability` 枚举。
3. 在 `CapabilityLoader` 链式调用中组装。

新增自定义能力（不改动现有代码）：
```java
new CapabilityLoader()
    .withCapability(StandardCapability.EXEC_TOOL, config)
    .withTool(new MyCustomTool())
    .withPromptPlugin(new MyCustomPromptPlugin())
    .build(provider, config);
```

配置驱动方式：
```java
new CapabilityLoader()
    .fromConfig("classpath:default-capabilities.json", config)
    .build(provider, config);
```

### 项目结构速查

```
com.coloop.agent
├── core/                       ← 教学核心，永远保持精简
│   ├── agent/
│   │   ├── AgentLoop.java      ← 核心 while 循环
│   │   └── AgentHook.java      ← 生命周期钩子接口
│   ├── message/
│   │   └── MessageBuilder.java ← 消息构建抽象接口
│   ├── prompt/
│   │   └── PromptPlugin.java   ← 提示词生成抽象接口
│   ├── provider/
│   │   ├── LLMProvider.java
│   │   ├── LLMResponse.java
│   │   └── ToolCallRequest.java
│   ├── tool/
│   │   ├── Tool.java
│   │   ├── BaseTool.java
│   │   └── ToolRegistry.java
│   └── interceptor/
│       └── InputInterceptor.java
├── capability/                 ← 可插拔能力实现
│   ├── message/
│   │   └── StandardMessageBuilder.java ← OpenAI 格式消息组装器
│   ├── prompt/
│   │   ├── PromptSegment.java
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
│   ├── CapabilityLoader.java
│   ├── StandardCapability.java
│   ├── AgentRuntime.java
│   └── config/
│       └── AppConfig.java
└── entry/
    ├── MinimalDemo.java
    └── CliApp.java
```

## 运行真实 API 的注意事项

切换至真实 API 模式时，需确保：
- 环境变量 `OPENAI_API_KEY` 已设置。
- `AppConfig` 中的 `apiBase` 和 `model` 已配置为正确的端点和模型名。
- 在 `entry.CliApp` 中切换为使用 `OpenAICompatibleProvider`。
