---
name: MCP Client Design
description: 基于 coloop-agent 架构的 MCP Client 实现设计
type: project
---

# MCP Client 实现设计

**创建日期**: 2026-04-20

## 目标

在 coloop-agent 项目中实现 MCP Client 能力，支持通过 STDIO 方式连接 MCP Server，将远程工具无缝接入 Agent 工具集。

## 背景

MCP (Model Context Protocol) 是一个开放标准，用于连接 AI 应用与外部系统（数据源、工具、工作流）。实现 MCP Client 后，coloop-agent 可接入 MCP 生态中的各种服务（如 Brave Search、Filesystem 等）。

## 架构设计

### 模块结构

```
capability/mcp/
├── McpClient.java           ← MCP 客户端核心，管理 STDIO 连接和协议
├── McpServerConfig.java     ← MCP Server 配置模型（来自 JSON）
├── McpToolAdapter.java      ← 将 MCP Tool 适配为本地 Tool 接口
├── McpTransport.java        ← STDIO 传输层实现
├── McpCapability.java       ← 能力封装，注册到 StandardCapability
├── JsonRpcRequest.java      ← JSON-RPC 请求模型
├── JsonRpcResponse.java     ← JSON-RPC 响应模型
└── McpToolDefinition.java   ← MCP Tool 定义模型
```

### 核心组件

#### 1. McpServerConfig

MCP Server 配置模型，映射 JSON 配置文件：

```java
public class McpServerConfig {
    private String command;        // 执行命令 (如 "npx", "python")
    private List<String> args;     // 命令参数
    private Map<String, String> env;  // 环境变量
}
```

#### 2. McpTransport

STDIO 传输层，负责：

- 通过 `ProcessBuilder` 启动 MCP Server 子进程
- 建立 STDIO 读写通道
- 发送/接收 JSON-RPC 消息
- 处理进程生命周期（启动、异常退出、重启）

#### 3. McpClient

MCP 客户端核心，实现 JSON-RPC 协议：

- `initialize()` — 协议握手，交换 client/server info
- `listTools()` — 获取可用工具列表
- `callTool(name, args)` — 调用指定工具

#### 4. McpToolAdapter

将 MCP Tool 适配为本地 `Tool` 接口：

```java
public class McpToolAdapter implements Tool {
    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final McpClient mcpClient;

    @Override
    public String execute(Map<String, Object> params) {
        return mcpClient.callTool(name, params);
    }
}
```

### 与现有架构的集成

#### 注册到 StandardCapability

```java
MCP_CLIENT(
    "mcp_client", "MCP 客户端", "通过 STDIO 连接 MCP Server 并暴露其工具",
    CapabilityType.TOOL,  // 注册为工具能力
    config -> new McpCapability(config)
)
```

#### CapabilityLoader 集成

```java
new CapabilityLoader()
    .withCapability(StandardCapability.MCP_CLIENT, config)
    .build(provider, config);
```

### 配置驱动方式

默认配置文件：`src/main/resources/mcp-servers-config.json`

```json
{
  "mcpServers": {
    "brave-search": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": {
        "BRAVE_API_KEY": "${BRAVE_API_KEY}"
      }
    },
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/"],
      "env": {}
    }
  }
}
```

通过 `AppConfig` 可自定义配置文件路径：

```java
appConfig.setMcpConfigPath("classpath:custom-mcp-servers.json");
```

## 核心流程

```
1. Agent 启动时
   └─ CapabilityLoader 加载 MCP_CLIENT 能力
       └─ McpCapability 读取配置文件
           └─ 创建 McpClient 实例（懒加载）

2. 首次调用 MCP 工具时
   └─ McpToolAdapter.execute() 被调用
       └─ McpClient 确保已连接（connectIfNeeded）
           └─ McpTransport 启动 Server 子进程
               └─ JSON-RPC 握手 (initialize)
               └─ 获取工具列表 (tools/list)
       └─ 发送工具调用请求 (tools/call)
           └─ 返回结果给 Agent

3. Agent 循环中
   └─ 工具结果作为 user message 传回 LLM
       └─ LLM 生成最终响应
```

## 错误处理

- **进程启动失败** — 抛出 `McpException`，包含退出码和错误输出
- **协议握手失败** — 解析 error response，重试一次后失败则终止
- **工具调用超时** — 配置 `request-timeout`，默认 60 秒
- **进程异常退出** — 检测到 EOF/异常时，尝试重启或标记工具不可用

## 安全考虑

- **环境变量隔离** — 仅传递配置中明确声明的环境变量
- **命令白名单** — 不支持用户自定义任意命令，仅限配置文件中声明的 server
- **输入校验** — MCP Server 返回的 tool schema 需校验格式

## 测试方案

### 单元测试

- `McpServerConfigTest` — 配置文件解析
- `JsonRpcRequest/ResponseTest` — JSON-RPC 序列化
- `McpToolAdapterTest` — 工具适配逻辑

### 集成测试

- 使用官方 `filesystem` MCP Server 测试文件操作
- 使用简单的 Echo Server 测试协议握手

## 扩展点

- **SSE 传输** — 未来可添加 `SseMcpTransport` 支持 HTTP 长连接
- **多 Server 并发** — 支持配置多个 MCP Server，工具名冲突时加前缀
- **资源 (Resources)** — MCP 不仅支持工具，还支持 Resources（文件、API 数据）
- **提示词 (Prompts)** — MCP 的 Prompt 模板能力

## 依赖

已有依赖：

- Jackson (`com.fasterxml.jackson.core:jackson-databind`) — JSON 序列化
- OkHttp (`com.squareup.okhttp3:okhttp`) — HTTP（如需 SSE）

需添加依赖（如有）：

- JDK 21 内置的 `ProcessBuilder` 已足够，无需额外依赖