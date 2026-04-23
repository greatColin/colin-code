# 聊天主题展示页面设计文档

## 目标
创建一个独立的 HTML 演示页面，展示 8 种不同风格的聊天 UI 主题，供开发者挑选并应用到 coloop-agent 项目中。

## 范围
- 单个独立 HTML 文件，零外部依赖（除 Google Fonts CDN 外）
- 纯展示用途，非生产功能
- 每个主题使用独立的 CSS 作用域，避免样式冲突

## 页面结构

### 顶部导航
- 页面标题："coloop-agent 主题选择器"
- 8 个主题的标签式快速跳转链接

### 主题 Demo 区块（共 8 个，纵向排列）
每个区块包含：
1. 主题名称标题 + 一句话风格描述
2. 固定尺寸聊天界面容器（约 650px 高）
3. 统一的演示内容（见下方）

### 演示内容（每个主题相同）
按顺序展示以下消息：
1. User: "分析一下这个项目的代码结构"
2. Loop Start: "Attempt 1..."
3. Thinking Card（默认折叠，显示 preview）
4. Tool-Call Card: `list_directory`（默认折叠）
5. Tool-Result Card: 目录列表（默认折叠）
6. Assistant: "这是一个 Maven 多模块项目，包含 core、capability、runtime、entry 四层架构..."
7. System: "已保存到会话历史"
8. 底部输入框（禁用状态，仅展示样式）

## 8 个主题定义

| 编号 | 主题名 | 参考来源 | 核心特征 |
|------|--------|----------|----------|
| T1 | Claude 优雅紫 | Claude.ai | 暖白/浅紫渐变背景，超大圆角气泡，细腻阴影 |
| T2 | ChatGPT 极简 | ChatGPT | 纯白底，极窄灰边框，绿色强调色，克制 |
| T3 | Cursor 极客暗 | Cursor IDE | 深色 slate 背景，高对比度边框，等宽代码字体 |
| T4 | Discord 社交暗 | Discord | 深灰底，彩色标签，卡片层次，年轻化 |
| T5 | Linear 专业白 | Linear | 黑白分明，超大留白，1px 细边框，秩序感 |
| T6 | Telegram 明快 | Telegram | 明亮蓝白，超大圆角，轻快活泼 |
| T7 | 终端复古 | Classic Terminal | 纯黑底，荧光绿文字，等宽字体，扫描线效果 |
| T8 | 玻璃华丽 | Modern Glassmorphism | 半透明毛玻璃，渐变流动背景，发光边框 |

## 技术方案

### 样式隔离
每个主题包裹在 `.theme-{name}` 容器中，所有子选择器以此开头：
```css
.theme-claude .message { ... }
.theme-claude .message.user { ... }
```

### 字体
- 通用：从 Google Fonts 引入特色字体对（Display + Body）
- 代码风主题使用等宽字体

### 交互
- Thinking/Tool 卡片的折叠/展开（与现有 chat.js 相同的简单 JS）
- 导航锚点跳转

## 输出文件
- `coloop-agent-server/src/main/resources/static/theme-showcase.html`

## 成功标准
- [ ] 页面可直接在浏览器打开，无需构建
- [ ] 8 个主题风格差异明显，各有记忆点
- [ ] 每个主题包含所有组件类型的展示
- [ ] 无样式泄漏，各主题相互独立
