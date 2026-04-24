# AGENTS.md - 项目约定

## 项目简介
coloop-agent 是一个基于 Java 的 AI Agent 框架，支持工具调用和多轮对话。

## 编码规范
- 使用 Java 17+ 特性
- 遵循阿里巴巴 Java 编码规范
- 类名使用 PascalCase，方法名使用 camelCase

## 项目结构
- `src/main/java/com/coloop/agent/entry/` - 入口类
- `src/main/java/com/coloop/agent/runtime/` - 运行时核心
- `src/main/java/com/coloop/agent/capability/` - 能力实现

## 工具使用约束

### exec 工具 - 严禁打开浏览器获取网页
- 工具 `exec`，它只能执行本地 shell 命令并获取其 stdout/stderr。
- **Windows 上 `start https://...` 只会打开浏览器给用户看，你完全获取不到网页内容，stdout 为空。**
- **macOS/Linux 上 `open` / `xdg-open` 同理，只会打开浏览器，你无法获取网页内容。**
- **绝对禁止**用 exec 执行 `start https://...`、`open https://...`、`xdg-open https://...` 或类似命令来尝试获取网页内容。
- 如果你需要网页上的信息，**直接向用户说明你无法浏览网页，并请用户将所需内容直接粘贴给你**。
- 不要为了给用户展示而打开浏览器；用户不需要看到浏览器窗口，只需要你基于已有信息给出最终结果。

## 测试要求
- 所有新功能需要编写单元测试
- 使用 JUnit 5 测试框架
