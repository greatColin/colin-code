# Web AI 消息渲染升级设计文档

## 背景与问题

当前 Web UI (`chat.js`) 的 AI 回答展示存在以下问题：

1. **换行不生效**：`renderAssistant` 使用 `textContent` 设置内容，HTML 中的 `\n` 被折叠为单个空格，导致控制台有换行但页面没有。
2. **`<think>` 标签未分离**：部分模型（如 DeepSeek R1）会将推理内容以 `<think>...</think>` 形式输出在普通回答中，当前被直接当作文本渲染，用户体验差。
3. **缺少 Markdown 渲染**：路线图 P0 要求集成 `marked.js`，实现粗体、列表、链接、表格、代码块等 Markdown 元素的渲染。
4. **缺少代码高亮**：路线图 P0 要求集成 `highlight.js`，为代码块提供语法高亮。

## 设计目标

- 修复换行显示问题
- 从 assistant 内容中提取 `<think>` 标签，用与现有 Thinking Card 一致的样式独立展示
- 集成 `marked.js` 实现 Markdown 渲染
- 集成 `highlight.js` 实现代码块语法高亮
- 使用 `DOMPurify` 对渲染后的 HTML 进行消毒，防止 XSS
- 适配全部 8 个主题的代码块样式

## 方案选择

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| A. 最小改动 | 仅加 CSS `white-space: pre-wrap` + 正则提取 think 标签 | 改动小、风险低 | Markdown 渲染和代码高亮仍未实现，路线图 P0 未推进 |
| **B. 一次到位** | 引入 `marked.js` + `highlight.js` + `DOMPurify`，先提取 think 标签，再对剩余内容做 Markdown 渲染 | 路线图 P0 功能一次性完成；marked.js 自然解决换行问题；XSS 有防护 | 改动面比 A 稍大 |
| C. 分阶段 | 先 A 修复问题，再另做一轮 Markdown 集成 | 每步风险低 | 额外迭代，范围集中，没必要拆分 |

**采用方案 B。** 所有改动集中在 `chat.js` 和主题 CSS 中，范围高度集中，一次到位最高效。

## 技术方案

### 1. CDN 资源引入

在 `index.html` `<head>` 中引入：

```html
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/dompurify@3.0.5/dist/purify.min.js"></script>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github.min.css" id="hljs-theme">
<script src="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"></script>
```

> 主题系统已存在，因此 `highlight.js` 主题 CSS 需支持主题切换。在 `theme-preview.html` 和 `theme-gallery.html` 中也需要同步引入。

### 2. Think 标签提取

在 `renderAssistant` 中，处理流程如下：

```
原始 content
    │
    ▼
正则提取 <think>...</think>
    │
    ├──→ thinkContent ──→ renderCard('thinking', '💭 Thinking', thinkContent)
    │
    └──→ remainingContent ──→ Markdown 渲染
```

正则要求：
- 匹配 `<think>` 和 `</think>` 标签（忽略大小写）
- 支持多行内容
- 从内容中彻底移除，避免重复渲染
- 如果提取出多个 think 块，全部合并到一个 thinking card 中

### 3. Markdown 渲染流程

剩余内容经过以下步骤：

1. **marked.parse()**：将 Markdown 转为原始 HTML
2. **DOMPurify.sanitize()**：消毒 HTML，移除危险标签和属性
3. **设置 innerHTML**：将安全 HTML 注入 assistant message
4. **hljs.highlightAll()**（或针对新插入元素的 `highlightElement`）：对 `<pre><code>` 代码块进行语法高亮

### 4. 向后兼容：纯文本 Fallback

如果 CDN 资源加载失败（如离线环境），`renderAssistant` 回退到现有行为：
- 使用 `textContent`
- 将 `\n` 替换为 `<br>` 以保留换行
- Think 标签仍提取并渲染为 card

检测方式：检查 `typeof marked !== 'undefined' && typeof DOMPurify !== 'undefined'`

### 5. 样式适配

#### 5.1 Assistant Message 内部 Markdown 元素

在每个主题的 CSS 中，为 `.message.assistant` 内的以下元素添加样式：

| 元素 | 样式要点 |
|------|---------|
| `p` | 段落间距 `margin: 0.5em 0` |
| `ul`, `ol` | 列表缩进 `padding-left: 1.5em`，`margin: 0.5em 0` |
| `li` | 行高 `line-height: 1.6` |
| `code`（行内） | 等宽字体、背景色、圆角、内边距 |
| `pre > code`（代码块） | 块级背景、圆角、内边距、溢出滚动、字体大小 |
| `blockquote` | 左边框、斜体、背景色 |
| `a` | 链接颜色、悬停下划线 |
| `table` | 边框、表头背景 |
| `hr` | 分割线颜色 |

#### 5.2 代码块高亮主题适配

每个主题 CSS 需定义 `hljs` 相关变量的配色，或选择与之匹配的 `highlight.js` 主题。

考虑到主题切换已有实现，采用以下策略：
- **浅色主题**（claude、chatgpt、linear）：使用 `github` hljs 主题
- **深色主题**（cursor、discord、terminal、glass）：使用 `github-dark` hljs 主题
- **telegram**：使用 `github` hljs 主题

在 `index.html` 的主题切换脚本中，同步切换 `hljs-theme` link 的 href。

### 6. XSS 防护

`DOMPurify.sanitize()` 配置：
- 默认配置即可满足需求
- 允许 `pre`, `code`, `span` 等标签（hljs 需要）
- 允许 `class` 属性（hljs 需要）
- 不需要允许 `script`, `style`, `iframe` 等危险标签

## 数据流

```
WebSocket onmessage
    │ type === 'assistant'
    ▼
renderAssistant(content)
    │
    ├── extractThinkBlocks(content)
    │       │
    │       └── 正则匹配 <think>[\s\S]*?</think>
    │
    ├── 如果有 think 内容
    │       └── renderCard('thinking', '💭 Thinking', thinkContent)
    │
    └── 对 remainingContent 做 Markdown 渲染
            │
            ├── marked.parse(remainingContent) → rawHtml
            ├── DOMPurify.sanitize(rawHtml) → safeHtml
            ├── el.innerHTML = safeHtml
            └── el.querySelectorAll('pre code').forEach(hljs.highlightElement)
```

## 测试计划

### 单元测试（前端）

由于当前项目没有前端测试框架，采用**浏览器内手动验证 + 后端 Java 测试**的组合：

1. **Think 提取逻辑测试**：构造包含 `<think>` 的内容字符串，验证提取和移除逻辑正确
2. **Markdown 渲染测试**：验证常见 Markdown 元素（标题、列表、代码块、链接、表格）渲染正确
3. **XSS 防护测试**：注入 `<script>alert(1)</script>`，验证被消毒
4. **主题适配测试**：切换全部 8 个主题，验证代码块高亮样式正确

### 验证用例

| 用例 | 输入 | 期望输出 |
|------|------|---------|
| 换行保留 | `"第一行\n第二行"` | 页面显示两行 |
| Think 提取 | `"<think>推理中...</think>最终答案"` | Thinking Card + "最终答案" |
| Markdown 粗体 | `"**粗体**"` | 加粗文字 |
| 代码块 | "\`\`\`java\nclass A {}\n\`\`\`" | 语法高亮的代码块 |
| XSS 防护 | `"<script>alert(1)</script>"` | 纯文本或安全 HTML |
| 多 Think 块 | `"<think>A</think>B<think>C</think>"` | 一个合并的 Thinking Card + "B" |

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `index.html` | 修改 | 引入 marked.js、highlight.js、DOMPurify CDN；主题切换同步切换 hljs 主题 |
| `chat.js` | 修改 | `renderAssistant` 增加 think 提取和 Markdown 渲染逻辑；添加 `extractThinkBlocks` 函数；添加 `renderMarkdown` 函数 |
| `themes/*.css` | 修改 | 为 `.message.assistant` 内的 Markdown 元素添加样式；为代码块添加背景/边框 |
| `theme-preview.html` | 修改 | 同步引入 CDN 资源 |
| `theme-gallery.html` | 修改 | 同步引入 CDN 资源 |

## 回滚策略

如果发现问题，可直接回滚 `chat.js` 和 `index.html` 到上一版本。CSS 的变更是增量的，不会破坏现有布局。
