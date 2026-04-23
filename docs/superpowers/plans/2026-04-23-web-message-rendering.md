# Web AI 消息渲染升级实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 AI 回答换行不生效问题，提取 `<think>` 标签独立渲染，集成 marked.js + highlight.js 实现 Markdown 渲染与代码高亮，XSS 消毒，适配全部 9 个主题。

**Architecture:** 前端 `chat.js` 在 `renderAssistant` 中先提取 think 标签，再对剩余内容用 `marked.js` 渲染 Markdown、`DOMPurify` 消毒、`highlight.js` 高亮代码块。主题 CSS 补充 Markdown 元素样式，主题切换联动 hljs 主题切换。

**Tech Stack:** Vanilla JS, marked.js, highlight.js, DOMPurify, CSS

---

## 文件结构

| 文件 | 变更 | 说明 |
|------|------|------|
| `coloop-agent-server/src/main/resources/static/index.html` | 修改 | 引入 CDN；主题切换同步 hljs 主题 |
| `coloop-agent-server/src/main/resources/static/chat.js` | 修改 | 新增 `extractThinkBlocks`、`renderMarkdown` 函数；改造 `renderAssistant` |
| `coloop-agent-server/src/main/resources/static/themes/*.css` (9个) | 修改 | 为 `.message.assistant` 内的 Markdown 元素添加样式 |
| `coloop-agent-server/src/main/resources/static/theme-preview.html` | 修改 | 引入 CDN，添加 Markdown 示例消息 |
| `coloop-agent-server/src/main/resources/static/test-rendering.html` | 新建 | 前端验证测试页面，覆盖所有用例 |

---

## Task 1: 引入 CDN 资源并联动主题切换

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/index.html`

**目标:** 在 `index.html` 中引入 marked.js、DOMPurify、highlight.js，并改造主题切换脚本使其同步切换 hljs 主题。

- [ ] **Step 1: 在 `<head>` 中引入 CDN 资源**

在 `index.html` 第 10 行（`<link id="theme-link" ...>`）之后添加：

```html
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/dompurify@3.0.5/dist/purify.min.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github.min.css" id="hljs-theme-link">
    <script src="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"></script>
```

- [ ] **Step 2: 改造主题切换脚本，同步切换 hljs 主题**

将 `index.html` 底部主题切换脚本（约第 92-113 行）替换为：

```javascript
    <script>
        (function() {
            var themeLink = document.getElementById('theme-link');
            var hljsThemeLink = document.getElementById('hljs-theme-link');
            var themeSelect = document.getElementById('theme-select');
            var STORAGE_KEY = 'coloop-agent-theme';
            var DEFAULT_THEME = 'claude';

            var DARK_THEMES = ['cursor', 'discord', 'terminal', 'glass'];

            function isDarkTheme(themeName) {
                return DARK_THEMES.indexOf(themeName) !== -1;
            }

            function loadTheme(themeName) {
                themeName = themeName || localStorage.getItem(STORAGE_KEY) || DEFAULT_THEME;
                themeLink.href = 'themes/' + themeName + '.css';
                if (themeSelect) themeSelect.value = themeName;
                // Sync highlight.js theme
                if (hljsThemeLink) {
                    var hljsTheme = isDarkTheme(themeName) ? 'github-dark' : 'github';
                    hljsThemeLink.href = 'https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/' + hljsTheme + '.min.css';
                }
            }

            if (themeSelect) {
                themeSelect.addEventListener('change', function() {
                    loadTheme(themeSelect.value);
                    localStorage.setItem(STORAGE_KEY, themeSelect.value);
                });
            }

            // Load saved theme on page load
            loadTheme();
        })();
    </script>
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/index.html
git commit -m "feat(ui): 引入 marked.js, highlight.js, DOMPurify CDN，主题切换联动代码高亮主题"
```

---

## Task 2: 实现 Think 提取与 Markdown 渲染函数

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/chat.js`

**目标:** 新增 `extractThinkBlocks` 和 `renderMarkdown` 函数，改造 `renderAssistant` 以支持 think 提取和 Markdown 渲染。

- [ ] **Step 1: 在 `chat.js` 顶部添加 `extractThinkBlocks` 函数**

在 `chat.js` 第 1 行（`(function() {`）之后、现有变量声明之前添加：

```javascript
    // --- Think tag extraction ---
    function extractThinkBlocks(content) {
        if (!content) return { thinkContent: '', remainingContent: '' };
        var thinkRegex = /<think>([\s\S]*?)<\/think>/gi;
        var thinkParts = [];
        var match;
        while ((match = thinkRegex.exec(content)) !== null) {
            thinkParts.push(match[1].trim());
        }
        var remainingContent = content.replace(thinkRegex, '').trim();
        return {
            thinkContent: thinkParts.join('\n\n'),
            remainingContent: remainingContent
        };
    }

    // --- Markdown rendering with XSS protection ---
    function renderMarkdown(content) {
        if (!content) return '';
        // Check if libraries are loaded (fallback for offline)
        if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
            // Fallback: plain text with BR for newlines
            return content.replace(/&/g, '&amp;')
                          .replace(/</g, '&lt;')
                          .replace(/>/g, '&gt;')
                          .replace(/\n/g, '<br>');
        }
        // Configure marked
        marked.setOptions({
            breaks: true,
            gfm: true,
            headerIds: false,
            mangle: false
        });
        var rawHtml = marked.parse(content);
        var safeHtml = DOMPurify.sanitize(rawHtml, {
            ALLOWED_TAGS: [
                'p', 'br', 'hr',
                'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
                'ul', 'ol', 'li',
                'strong', 'em', 'del', 'code', 'pre',
                'a', 'img',
                'blockquote',
                'table', 'thead', 'tbody', 'tr', 'th', 'td',
                'div', 'span'
            ],
            ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'target']
        });
        return safeHtml;
    }

    // --- Apply syntax highlighting to code blocks within an element ---
    function highlightCodeBlocks(container) {
        if (typeof hljs === 'undefined') return;
        var codeBlocks = container.querySelectorAll('pre code');
        codeBlocks.forEach(function(block) {
            hljs.highlightElement(block);
        });
    }
```

- [ ] **Step 2: 改造 `renderAssistant` 函数**

将现有的 `renderAssistant` 函数（第 106-111 行）：

```javascript
    function renderAssistant(content) {
        const el = document.createElement('div');
        el.className = 'message assistant';
        el.textContent = content;
        appendElement(el);
    }
```

替换为：

```javascript
    function renderAssistant(content) {
        // Extract think blocks from content
        var extracted = extractThinkBlocks(content || '');

        // Render think content as a thinking card (if any)
        if (extracted.thinkContent) {
            renderCard('thinking', '💭 Thinking', extracted.thinkContent);
        }

        // Render remaining content as markdown
        var el = document.createElement('div');
        el.className = 'message assistant';
        el.innerHTML = renderMarkdown(extracted.remainingContent);
        appendElement(el);

        // Apply syntax highlighting to code blocks
        highlightCodeBlocks(el);
    }
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/chat.js
git commit -m "feat(ui): 实现 think 标签提取、Markdown 渲染、代码高亮与 XSS 消毒"
```

---

## Task 3: 适配默认主题 (claude.css) 的 Markdown 样式

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/themes/claude.css`

**目标:** 为 `.message.assistant` 内的 Markdown 元素（段落、列表、代码块、引用、表格、链接等）添加样式。

- [ ] **Step 1: 在 `claude.css` 末尾添加 Markdown 元素样式**

在 `claude.css` 文件末尾（第 333 行之后）添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p {
    margin: 0.5em 0;
}
.message.assistant p:first-child {
    margin-top: 0;
}
.message.assistant p:last-child {
    margin-bottom: 0;
}

.message.assistant ul,
.message.assistant ol {
    padding-left: 1.5em;
    margin: 0.5em 0;
}

.message.assistant li {
    line-height: 1.6;
    margin: 0.2em 0;
}

.message.assistant code {
    font-family: 'JetBrains Mono', 'IBM Plex Mono', monospace;
    font-size: 0.9em;
    background: #f3f4f6;
    color: #374151;
    padding: 2px 6px;
    border-radius: 4px;
}

.message.assistant pre {
    background: #1e1e2e;
    border-radius: 8px;
    padding: 12px 16px;
    overflow-x: auto;
    margin: 0.5em 0;
}

.message.assistant pre code {
    background: transparent;
    color: #e4e4e7;
    padding: 0;
    font-size: 13px;
    line-height: 1.5;
}

.message.assistant blockquote {
    border-left: 3px solid #ddd6fe;
    padding-left: 12px;
    margin: 0.5em 0;
    color: #6b7280;
    font-style: italic;
}

.message.assistant a {
    color: #7c3aed;
    text-decoration: none;
}

.message.assistant a:hover {
    text-decoration: underline;
}

.message.assistant table {
    border-collapse: collapse;
    margin: 0.5em 0;
    font-size: 13px;
}

.message.assistant th,
.message.assistant td {
    border: 1px solid #e5e7eb;
    padding: 6px 10px;
    text-align: left;
}

.message.assistant th {
    background: #f9fafb;
    font-weight: 600;
}

.message.assistant hr {
    border: none;
    border-top: 1px solid #e5e7eb;
    margin: 1em 0;
}
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/themes/claude.css
git commit -m "style(theme): claude 主题添加 Markdown 元素样式"
```

---

## Task 4: 适配其余 8 个主题的 Markdown 样式

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/themes/chatgpt.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/cursor.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/discord.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/linear.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/telegram.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/terminal.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/glass.css`
- Modify: `coloop-agent-server/src/main/resources/static/themes/default.css`

**目标:** 在每个主题 CSS 末尾添加与主题配色一致的 Markdown 元素样式。

- [ ] **Step 1: 为 chatgpt.css 添加 Markdown 样式**

在 `chatgpt.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #f3f4f6; color: #374151; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #1e1e2e; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; }
.message.assistant pre code { background: transparent; color: #e4e4e7; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #10a37f; padding-left: 12px; margin: 0.5em 0; color: #6b7280; font-style: italic; }
.message.assistant a { color: #10a37f; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #f9fafb; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #e5e7eb; margin: 1em 0; }
```

- [ ] **Step 2: 为 cursor.css 添加 Markdown 样式**

在 `cursor.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #1e293b; color: #e2e8f0; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #0f172a; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; border: 1px solid #334155; }
.message.assistant pre code { background: transparent; color: #e2e8f0; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #6366f1; padding-left: 12px; margin: 0.5em 0; color: #94a3b8; font-style: italic; }
.message.assistant a { color: #818cf8; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #334155; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #1e293b; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #334155; margin: 1em 0; }
```

- [ ] **Step 3: 为 discord.css 添加 Markdown 样式**

在 `discord.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #2f3136; color: #dcddde; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #2f3136; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; }
.message.assistant pre code { background: transparent; color: #dcddde; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #7289da; padding-left: 12px; margin: 0.5em 0; color: #99aab5; font-style: italic; }
.message.assistant a { color: #7289da; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #40444b; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #2f3136; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #40444b; margin: 1em 0; }
```

- [ ] **Step 4: 为 linear.css 添加 Markdown 样式**

在 `linear.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #f3f4f6; color: #111827; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #111827; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; }
.message.assistant pre code { background: transparent; color: #e5e7eb; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #e5e7eb; padding-left: 12px; margin: 0.5em 0; color: #6b7280; font-style: italic; }
.message.assistant a { color: #2563eb; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #f9fafb; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #e5e7eb; margin: 1em 0; }
```

- [ ] **Step 5: 为 telegram.css 添加 Markdown 样式**

在 `telegram.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #e8f4fd; color: #168acd; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #1e1e2e; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; }
.message.assistant pre code { background: transparent; color: #e4e4e7; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #6ab4f0; padding-left: 12px; margin: 0.5em 0; color: #6b7280; font-style: italic; }
.message.assistant a { color: #168acd; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #dbeafe; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #eff6ff; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #dbeafe; margin: 1em 0; }
```

- [ ] **Step 6: 为 terminal.css 添加 Markdown 样式**

在 `terminal.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #1a1a1a; color: #39ff14; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #0c0c0c; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; border: 1px solid #39ff14; }
.message.assistant pre code { background: transparent; color: #39ff14; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #39ff14; padding-left: 12px; margin: 0.5em 0; color: #888; font-style: italic; }
.message.assistant a { color: #39ff14; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #333; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #1a1a1a; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #333; margin: 1em 0; }
```

- [ ] **Step 7: 为 glass.css 添加 Markdown 样式**

在 `glass.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: rgba(255,255,255,0.15); color: #e0e7ff; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: rgba(0,0,0,0.4); border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; border: 1px solid rgba(255,255,255,0.1); }
.message.assistant pre code { background: transparent; color: #e0e7ff; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid rgba(255,255,255,0.3); padding-left: 12px; margin: 0.5em 0; color: #a5b4fc; font-style: italic; }
.message.assistant a { color: #a5b4fc; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid rgba(255,255,255,0.15); padding: 6px 10px; text-align: left; }
.message.assistant th { background: rgba(255,255,255,0.1); font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid rgba(255,255,255,0.15); margin: 1em 0; }
```

- [ ] **Step 8: 为 default.css 添加 Markdown 样式**

在 `default.css` 末尾添加：

```css
/* Markdown elements inside assistant message */
.message.assistant p { margin: 0.5em 0; }
.message.assistant p:first-child { margin-top: 0; }
.message.assistant p:last-child { margin-bottom: 0; }
.message.assistant ul, .message.assistant ol { padding-left: 1.5em; margin: 0.5em 0; }
.message.assistant li { line-height: 1.6; margin: 0.2em 0; }
.message.assistant code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; background: #f3f4f6; color: #374151; padding: 2px 6px; border-radius: 4px; }
.message.assistant pre { background: #1e1e2e; border-radius: 8px; padding: 12px 16px; overflow-x: auto; margin: 0.5em 0; }
.message.assistant pre code { background: transparent; color: #e4e4e7; padding: 0; font-size: 13px; line-height: 1.5; }
.message.assistant blockquote { border-left: 3px solid #d1d5db; padding-left: 12px; margin: 0.5em 0; color: #6b7280; font-style: italic; }
.message.assistant a { color: #2563eb; text-decoration: none; }
.message.assistant a:hover { text-decoration: underline; }
.message.assistant table { border-collapse: collapse; margin: 0.5em 0; font-size: 13px; }
.message.assistant th, .message.assistant td { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
.message.assistant th { background: #f9fafb; font-weight: 600; }
.message.assistant hr { border: none; border-top: 1px solid #e5e7eb; margin: 1em 0; }
```

- [ ] **Step 9: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/themes/
git commit -m "style(theme): 为全部 9 个主题添加 Markdown 元素样式"
```

---

## Task 5: 同步更新 theme-preview.html

**Files:**
- Modify: `coloop-agent-server/src/main/resources/static/theme-preview.html`

**目标:** 引入 CDN 资源，添加一个包含 Markdown 元素的 assistant 消息示例，使主题预览能展示 Markdown 渲染效果。

- [ ] **Step 1: 在 `theme-preview.html` 的 `<head>` 中引入 CDN**

在 `<link id="theme-link" ...>` 之后添加：

```html
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/dompurify@3.0.5/dist/purify.min.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github.min.css" id="hljs-theme-link">
    <script src="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"></script>
```

- [ ] **Step 2: 将现有的 assistant 消息示例替换为 Markdown 示例**

将现有的 `theme-preview.html` 第 64 行：

```html
            <div class="msg message assistant">这是一个 Maven 多模块项目，包含 core、capability、runtime、entry 四层架构...</div>
```

替换为：

```html
            <div class="msg message assistant"><p>这是一个 <strong>Maven</strong> 多模块项目，包含以下架构：</p>
<ul>
<li><code>core/</code> - 教学骨架</li>
<li><code>capability/</code> - 可插拔能力</li>
<li><code>runtime/</code> - 动态组装中枢</li>
</ul>
<pre><code class="language-java">public class AgentLoop {
    public void chat() {
        // core loop
    }
}
</code></pre>
</div>
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/theme-preview.html
git commit -m "feat(ui): theme-preview 引入 CDN 并添加 Markdown 示例消息"
```

---

## Task 6: 创建前端验证测试页面

**Files:**
- Create: `coloop-agent-server/src/main/resources/static/test-rendering.html`

**目标:** 创建一个独立的前端测试页面，覆盖所有验证用例：换行保留、Think 提取、Markdown 粗体、代码块、XSS 防护、多 Think 块。

- [ ] **Step 1: 创建 `test-rendering.html`**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>渲染测试 - coloop-agent</title>
    <link rel="stylesheet" href="themes/claude.css">
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/dompurify@3.0.5/dist/purify.min.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github.min.css">
    <script src="https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js"></script>
    <style>
        .test-suite { padding: 20px; max-width: 900px; margin: 0 auto; }
        .test-case { margin-bottom: 30px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; }
        .test-title { font-weight: 600; font-size: 16px; margin-bottom: 8px; color: #374151; }
        .test-status { font-size: 12px; padding: 2px 8px; border-radius: 12px; display: inline-block; margin-bottom: 12px; }
        .test-status.pass { background: #d4edda; color: #155724; }
        .test-status.fail { background: #fee2e2; color: #991b1b; }
        .test-input { background: #f9fafb; padding: 10px; border-radius: 6px; font-family: monospace; font-size: 12px; margin-bottom: 12px; white-space: pre-wrap; }
        .test-output { border: 1px dashed #d1d5db; border-radius: 6px; padding: 10px; }
        .assertion { font-size: 12px; margin-top: 8px; }
        .assertion.pass { color: #059669; }
        .assertion.fail { color: #dc2626; }
    </style>
</head>
<body>
    <div class="test-suite">
        <h1>Web 消息渲染测试</h1>
        <div id="test-results"></div>
    </div>

    <script>
        // Copy functions from chat.js for testing
        function extractThinkBlocks(content) {
            if (!content) return { thinkContent: '', remainingContent: '' };
            var thinkRegex = /<think>([\s\S]*?)<\/think>/gi;
            var thinkParts = [];
            var match;
            while ((match = thinkRegex.exec(content)) !== null) {
                thinkParts.push(match[1].trim());
            }
            var remainingContent = content.replace(thinkRegex, '').trim();
            return {
                thinkContent: thinkParts.join('\n\n'),
                remainingContent: remainingContent
            };
        }

        function renderMarkdown(content) {
            if (!content) return '';
            if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
                return content.replace(/&/g, '&amp;')
                              .replace(/</g, '&lt;')
                              .replace(/>/g, '&gt;')
                              .replace(/\n/g, '<br>');
            }
            marked.setOptions({ breaks: true, gfm: true, headerIds: false, mangle: false });
            var rawHtml = marked.parse(content);
            var safeHtml = DOMPurify.sanitize(rawHtml, {
                ALLOWED_TAGS: ['p','br','hr','h1','h2','h3','h4','h5','h6','ul','ol','li','strong','em','del','code','pre','a','img','blockquote','table','thead','tbody','tr','th','td','div','span'],
                ALLOWED_ATTR: ['href','src','alt','title','class','target']
            });
            return safeHtml;
        }

        function highlightCodeBlocks(container) {
            if (typeof hljs === 'undefined') return;
            container.querySelectorAll('pre code').forEach(function(block) {
                hljs.highlightElement(block);
            });
        }

        // Test runner
        var testResults = [];
        var container = document.getElementById('test-results');

        function runTest(name, input, assertions) {
            var testDiv = document.createElement('div');
            testDiv.className = 'test-case';

            var title = document.createElement('div');
            title.className = 'test-title';
            title.textContent = name;
            testDiv.appendChild(title);

            var inputDiv = document.createElement('div');
            inputDiv.className = 'test-input';
            inputDiv.textContent = typeof input === 'string' ? input : JSON.stringify(input, null, 2);
            testDiv.appendChild(inputDiv);

            var outputDiv = document.createElement('div');
            outputDiv.className = 'test-output';

            var result;
            if (typeof input === 'string') {
                var extracted = extractThinkBlocks(input);
                var html = renderMarkdown(extracted.remainingContent);
                outputDiv.innerHTML = html;
                highlightCodeBlocks(outputDiv);
                result = { html: html, thinkContent: extracted.thinkContent, remainingContent: extracted.remainingContent };
            } else {
                result = input;
            }
            testDiv.appendChild(outputDiv);

            var allPass = true;
            assertions.forEach(function(assert) {
                var pass = assert.fn(result, outputDiv);
                var assertDiv = document.createElement('div');
                assertDiv.className = 'assertion ' + (pass ? 'pass' : 'fail');
                assertDiv.textContent = (pass ? 'PASS' : 'FAIL') + ': ' + assert.desc;
                testDiv.appendChild(assertDiv);
                if (!pass) allPass = false;
            });

            var status = document.createElement('span');
            status.className = 'test-status ' + (allPass ? 'pass' : 'fail');
            status.textContent = allPass ? '通过' : '失败';
            testDiv.insertBefore(status, inputDiv);

            container.appendChild(testDiv);
            testResults.push({ name: name, pass: allPass });
        }

        // Test 1: 换行保留
        runTest('换行保留', '第一行\n第二行\n第三行', [
            { desc: '输出包含 <p> 标签', fn: function(r) { return r.html.indexOf('<p>') !== -1; } },
            { desc: '输出包含 "第一行" 文本', fn: function(r) { return r.html.indexOf('第一行') !== -1; } }
        ]);

        // Test 2: Think 提取
        runTest('Think 提取', '<think>推理中...\n步骤1</think>\n最终答案是 42', [
            { desc: 'thinkContent 不为空', fn: function(r) { return r.thinkContent.length > 0; } },
            { desc: 'thinkContent 包含 "推理中"', fn: function(r) { return r.thinkContent.indexOf('推理中') !== -1; } },
            { desc: 'remainingContent 包含 "42"', fn: function(r) { return r.remainingContent.indexOf('42') !== -1; } },
            { desc: 'remainingContent 不含 think 标签', fn: function(r) { return r.remainingContent.indexOf('<think>') === -1; } }
        ]);

        // Test 3: Markdown 粗体
        runTest('Markdown 粗体', '这是 **粗体** 文字', [
            { desc: '输出包含 <strong> 标签', fn: function(r) { return r.html.indexOf('<strong>') !== -1; } }
        ]);

        // Test 4: 代码块
        runTest('代码块', '\`\`\`java\nclass Hello {\n    public static void main(String[] args) {\n        System.out.println("Hello");\n    }\n}\n\`\`\`', [
            { desc: '输出包含 <code> 标签', fn: function(r) { return r.html.indexOf('<code>') !== -1; } },
            { desc: '输出包含 <pre> 标签', fn: function(r) { return r.html.indexOf('<pre>') !== -1; } },
            { desc: '输出包含 "Hello" 文本', fn: function(r) { return r.html.indexOf('Hello') !== -1; } }
        ]);

        // Test 5: XSS 防护
        runTest('XSS 防护', '<script>alert(1)</script> 正常文字', [
            { desc: '输出不含 <script> 标签', fn: function(r) { return r.html.indexOf('<script>') === -1; } },
            { desc: '输出包含 "正常文字"', fn: function(r) { return r.html.indexOf('正常文字') !== -1; } }
        ]);

        // Test 6: 多 Think 块
        runTest('多 Think 块合并', '<think>思考A</think>中间内容<think>思考B</think>结尾', [
            { desc: 'thinkContent 包含 "思考A"', fn: function(r) { return r.thinkContent.indexOf('思考A') !== -1; } },
            { desc: 'thinkContent 包含 "思考B"', fn: function(r) { return r.thinkContent.indexOf('思考B') !== -1; } },
            { desc: 'remainingContent 是 "中间内容 结尾"', fn: function(r) { return r.remainingContent.replace(/\s+/g, ' ').trim() === '中间内容 结尾'; } }
        ]);

        // Summary
        var passed = testResults.filter(function(t) { return t.pass; }).length;
        var summary = document.createElement('div');
        summary.style.cssText = 'margin-top:20px; padding:16px; border-radius:8px; font-weight:600;';
        summary.style.background = passed === testResults.length ? '#d4edda' : '#fee2e2';
        summary.style.color = passed === testResults.length ? '#155724' : '#991b1b';
        summary.textContent = '测试结果: ' + passed + '/' + testResults.length + ' 通过';
        container.insertBefore(summary, container.firstChild);
    </script>
</body>
</html>
```

- [ ] **Step 2: 在浏览器中打开测试页面验证**

用浏览器打开 `test-rendering.html`（需要启动服务器或者直接用 file:// 协议），验证全部 6 个测试用例通过。

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-server/src/main/resources/static/test-rendering.html
git commit -m "test(ui): 添加前端渲染验证测试页面"
```

---

## Task 7: 编译打包并运行验证

**Files:**
- (验证已有修改)

**目标:** 编译打包项目，启动服务器，在浏览器中实际验证功能。

- [ ] **Step 1: 编译打包**

```bash
mvn clean package -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: 启动服务器**

```bash
cd coloop-agent-server
mvn exec:java -Dexec.mainClass="com.coloop.agent.server.ColoopAgentServer" &
```

Expected: 服务器启动，监听 WebSocket。

- [ ] **Step 3: 浏览器验证**

打开浏览器访问：
1. `http://localhost:8080/test-rendering.html` - 确认 6 个测试全部通过
2. `http://localhost:8080/` - 发送消息，确认：
   - AI 回答中的换行正确显示
   - 如果回答包含 `<think>...</think>`，think 内容显示为可折叠 Thinking Card
   - Markdown 粗体、列表、代码块等正确渲染
   - 代码块有语法高亮
3. `http://localhost:8080/theme-gallery.html` - 切换各主题，确认代码块高亮主题同步切换

- [ ] **Step 4: Commit（如有修复）**

```bash
git add -A
git commit -m "fix(ui): 根据浏览器验证修复渲染问题"
```

---

## Spec Coverage 检查

| 设计文档要求 | 对应 Task |
|-------------|-----------|
| 引入 marked.js, highlight.js, DOMPurify CDN | Task 1 |
| 主题切换同步 hljs 主题 | Task 1 |
| Think 标签提取 | Task 2 |
| Markdown 渲染 + XSS 消毒 | Task 2 |
| 代码高亮 | Task 2 |
| 纯文本 fallback | Task 2 (`renderMarkdown` offline 分支) |
| 主题 CSS 适配 (9 个主题) | Task 3 + Task 4 |
| theme-preview 同步 | Task 5 |
| 前端测试验证 | Task 6 |
| 编译运行验证 | Task 7 |

**无遗漏。**

## Placeholder 检查

- 无 "TBD", "TODO", "implement later"
- 无 "Add appropriate error handling" 等模糊描述
- 每个步骤包含完整代码或命令
- 无 "Similar to Task N" 引用

**无占位符。**

## Type Consistency 检查

- `extractThinkBlocks` 返回结构一致：`{ thinkContent, remainingContent }`
- `renderMarkdown` 签名一致：接收 `content`，返回 HTML 字符串
- `highlightCodeBlocks` 签名一致：接收 DOM 容器
- `renderAssistant` 改造后内部调用一致

**类型一致。**
