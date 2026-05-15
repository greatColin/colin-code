# Voice Input IME Design

日期: 2026-05-12

## 目标

将语音输入模块重构为输入法形态，支持 iframe 嵌入和 exe 打包。

## 核心变更

### 1. 录音方式简化

**当前**：AudioWorklet + 实时流式传输
**目标**：MediaRecorder（浏览器原生）

- 录音结束后自动触发识别
- 无需手动管理音频缓冲区
- 浏览器兼容性更好

### 2. 通信方式简化

**当前**：Socket.IO 实时双向通信
**目标**：HTTP REST 单次请求

- `POST /api/recognize`：上传音频文件，返回识别结果
- 无需维护 WebSocket 连接
- 更易调试和部署

### 3. 识别模式精简

**当前**：realtime / realtime_final / final_only 三种模式
**目标**：仅保留一次性识别（录完再识别）

理由：分段式识别效果不佳，整体识别+后续纠错更可靠。

### 4. 配置精简

**当前**：识别模式、实时纠错、后处理纠错、结束操作等 5+ 配置
**目标**：仅保留 2 个配置

| 配置项 | 类型 | 说明 |
|--------|------|------|
| LLM纠错 | 开关 | 是否启用 LLM 后处理纠错 |
| 识别后操作 | 单选 | 无操作 / 复制到剪贴板 |

## 页面布局

### 输入法形态（长条状）

```
┌──────────────────────────────────────────────────────────┐
│                    [气泡：识别结果/状态]                     │
├──────────────────────────────────────────────────────────┤
│   🎙️   │   ▁▂▃▅▆▇▆▅▃▂▁   │   ⚙️                        │
│  按钮   │     声纹可视化     │  设置按钮                      │
└──────────────────────────────────────────────────────────┘
```

### 气泡行为

| 状态 | 显示内容 | 样式 |
|------|----------|------|
| 空闲 | 隐藏 | - |
| 录音中 | 隐藏 | - |
| 识别中 | "识别中..." + 旋转图标 | 蓝色 |
| 纠错中 | "纠错中..." + 脉冲动画 | 紫色 |
| 完成 | 识别结果文本 | 白色，淡入 |
| 错误 | 错误信息 | 红色 |

### 设置面板

点击 ⚙️ 后从下方弹出：

```
┌──────────────────────────────────────────────────────────┐
│   🎙️   │   ▁▂▃▅▆▇▆▅▃▂▁   │   ⚙️                        │
├──────────────────────────────────────────────────────────┤
│  LLM纠错  [====●]                                        │
│  识别后    ○ 无操作  ● 复制到剪贴板                         │
└──────────────────────────────────────────────────────────┘
```

## 技术实现

### 前端

- **单文件**：`voice-input.html`（内联 CSS + JS）
- **录音**：MediaRecorder API
- **通信**：Fetch API + FormData
- **声纹**：Canvas + AnalyserNode（仅可视化，不参与录音）
- **嵌入**：postMessage API

### 后端

- **端点**：`POST /api/recognize`
- **请求**：`multipart/form-data`（音频文件 + 配置参数）
- **响应**：`{ "text": "识别结果", "corrected": true }`
- **处理**：faster-whisper 识别 → 可选 LLM 纠错

### API 设计

```
POST /api/recognize
Content-Type: multipart/form-data

Fields:
  - audio: Blob (webm/opus 或 wav)
  - enable_correction: boolean (是否启用 LLM 纠错)

Response:
  {
    "text": "最终识别结果",
    "raw_text": "原始识别结果（未纠错）",
    "corrected": true,
    "duration_ms": 1234
  }
```

### 嵌入接口

```javascript
// 父页面监听语音输入结果
window.addEventListener('message', (event) => {
  if (event.data.type === 'voice-result') {
    console.log('识别结果:', event.data.text);
    console.log('是否纠错:', event.data.corrected);
  }
});

// 父页面控制语音输入
const voiceFrame = document.getElementById('voice-input');
voiceFrame.contentWindow.postMessage({ type: 'voice-start' }, '*');
voiceFrame.contentWindow.postMessage({ type: 'voice-stop' }, '*');
```

## 文件结构

```
coloop-agent-voice/
├── static/
│   └── voice-input.html      # 单文件（HTML + CSS + JS）
├── main.py                    # FastAPI 服务
├── api/
│   └── recognize.py           # /api/recognize 端点
├── engine/
│   └── whisper_engine.py      # Whisper 引擎（保留）
├── correction/
│   └── post_corrector.py      # LLM 纠错（保留）
└── config.py                  # 配置（精简）
```

## 与现有代码的关系

### 保留
- `engine/whisper_engine.py`：Whisper 引擎
- `correction/post_corrector.py`：LLM 纠错
- `audio/energy_vad.py`：VAD（可选，用于检测静音）

### 删除
- `audio/vad_processor.py`：流式 VAD（不再需要）
- `correction/streaming_diff.py`：实时纠错（不再需要）
- `session/voice_session.py`：会话管理（改为无状态）
- `engine/websocket_adapter.py`：WebSocket 适配器（改为 HTTP）
- `engine/http_adapter.py`：旧 HTTP 适配器（重写）

### 修改
- `static/voice-input.html`：完全重写
- `main.py`：简化为 FastAPI + 单端点
- `config.py`：精简配置项

## 打包支持

### Electron 打包

```json
{
  "name": "voice-input-ime",
  "main": "main.js",
  "build": {
    "files": ["static/**", "main.py", "requirements.txt"]
  }
}
```

### Tauri 打包（更轻量）

```toml
[tauri.bundle]
active = true
identifier = "com.coloop.voice-input"
```

## 验证标准

1. 页面加载后显示长条状输入法界面
2. 点击麦克风按钮开始录音，声纹实时显示
3. 再次点击停止录音，自动触发识别
4. 气泡显示"识别中..."，完成后显示结果
5. 如果启用纠错，气泡显示"纠错中..."
6. 识别结果可通过 postMessage 发送到父页面
7. 设置面板可切换 LLM 纠错和识别后操作
