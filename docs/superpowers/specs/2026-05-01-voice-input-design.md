# 语音输入模块设计文档

## 1. 概述

独立语音输入服务 `coloop-agent-voice`，基于 Python + faster-whisper，提供流式语音识别与可配置纠错能力。该模块自成一体，不依赖现有 coloop-agent Java 项目，可作为独立服务运行。用户通过网页端进行语音输入，识别结果实时展示在底部 textarea 中，支持一键复制到其他软件。

## 2. 目标

- **流式语音识别**：说话的同时进行语音识别，文本逐句/逐段实时出现
- **可配置纠错**：支持段内流式修正（实时更新）和整体后处理纠错（LLM 润色），两者可独立开关
- **本地模型优先**：默认使用 faster-whisper 本地运行，支持多模型尺寸切换
- **模块独立**：纯 Python 服务，独立启动，独立部署
- **模型配置复用**：LLM 后处理纠错的 API 配置复用现有 `coloop-agent-setting.json` 中的模型定义

## 3. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                        浏览器                                │
│  ┌─────────────┐    Web Audio API    ┌──────────────────┐  │
│  │ 麦克风按钮   │ ──→ 采集 16kHz PCM ──→ │ WebSocket Client  │  │
│  └─────────────┘                      └──────────────────┘  │
│         ↑                                    ↓ binary       │
│  ┌─────────────┐                      ┌──────────────────┐  │
│  │ 实时文本展示 │ ←──────────────────── │ 前端 JS 逻辑      │  │
│  └─────────────┘    partial/final     └──────────────────┘  │
│         ↑                                                   │
│  ┌─────────────┐                                            │
│  │ 结果 textarea│ ←── complete / post_corrected              │
│  │  + 复制按钮  │                                            │
│  └─────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (/ws/voice)
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              coloop-agent-voice (Python / FastAPI)          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           VoiceWebSocketHandler                      │   │
│  │  接收 binary PCM chunks + 控制消息(start/stop)       │   │
│  └─────────────────────────────────────────────────────┘   │
│                              ↓                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         VoiceSessionService (每连接一个会话)          │   │
│  │  - 音频缓冲区管理                                      │   │
│  │  - VAD 语音检测                                        │   │
│  │  - 调度识别引擎                                        │   │
│  │  - 组合纠错管道                                        │   │
│  └─────────────────────────────────────────────────────┘   │
│          ↓              ↓              ↓                    │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────────────┐   │
│  │VAD Processor│ │WhisperEngine│ │CorrectionService │   │
│  │(webrtcvad)  │ │(faster-whisper)│  - streaming_diff │   │
│  └─────────────┘ └─────────────┘  - post_corrector  │   │
│                                    └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 4. 技术选型

| 组件 | 选型 | 理由 |
|---|---|---|
| Web 框架 | **FastAPI** + `python-socketio` | 轻量、原生异步、WebSocket 支持完善 |
| 本地 ASR | **faster-whisper** | CTranslate2 加速，比原版 Whisper 快 4x，支持 CPU/GPU，模型尺寸灵活 |
| VAD | **webrtcvad** (首选) / **silero-vad** (备选) | 轻量实时语音活动检测，决定识别触发时机 |
| 音频处理 | **numpy** | PCM bytes → float32 归一化 |
| 后处理纠错 | **httpx** | 异步 HTTP 客户端，调用 OpenAI 兼容 API |
| 前端 | 纯 HTML/JS | 单页面应用，零构建步骤，直接浏览器打开 |
| 配置 | **python-dotenv** + JSON | `.env` 放环境变量，独立 JSON 文件放模型配置（复用现有格式） |

## 5. 目录结构

```
coloop-agent-voice/
├── main.py                         ← FastAPI 应用入口
├── config.py                       ← 配置加载（兼容 coloop-agent-setting.json）
├── requirements.txt
├── .env.example
├── README.md
├── engine/
│   ├── __init__.py
│   ├── whisper_engine.py           ← faster-whisper 封装
│   └── http_adapter.py             ← 外部 HTTP 引擎适配（预留）
├── audio/
│   ├── __init__.py
│   └── vad_processor.py            ← VAD 检测与音频切分
├── correction/
│   ├── __init__.py
│   ├── streaming_diff.py           ← 文本增量 diff
│   └── post_corrector.py           ← LLM 后处理纠错
├── session/
│   ├── __init__.py
│   └── voice_session.py            ← 单次连接会话状态管理
├── static/
│   ├── voice-input.html            ← 语音输入器页面
│   └── voice-input.js              ← 音频采集 + WebSocket + UI
└── models/                         ← Whisper 模型目录（gitignored）
```

## 6. 核心模块设计

### 6.1 WhisperEngine

封装 faster-whisper，提供增量识别接口：

- `__init__(model_path, device, compute_type)` — 加载模型
- `transcribe(audio_np: np.ndarray, lang: str) -> List[Segment]` — 识别一段音频
- `condition_on_previous_text=True` — 关键参数，保证上下文连贯性

### 6.2 VADProcessor

基于 webrtcvad 检测语音起止：

- `process_chunk(pcm_bytes: bytes) -> Optional[bytes]` — 输入 30ms 的 PCM 帧，返回检测到的语音段
- `is_speech(pcm_bytes) -> bool` — 单帧是否包含语音
- `flush() -> Optional[bytes]` — 强制输出缓冲区中剩余语音

### 6.3 VoiceSession

每路 WebSocket 连接维护一个会话：

```python
class VoiceSession:
    config: VoiceConfig          # 识别配置
    audio_buffer: bytearray      # 累积音频
    last_text: str               # 上次识别结果（用于 diff）
    segment_index: int           # 当前段序号
    is_speaking: bool            # 是否处于说话状态
    
    async def feed_audio(self, pcm: bytes):
        # 1. VAD 检测
        # 2. 累积到缓冲区
        # 3. 达到触发条件 → 调用 WhisperEngine
        # 4. diff 对比 → 发送 partial / segment_final
        # 5. 如开启后处理 → 异步调用 PostCorrector
```

### 6.4 CorrectionService

**streaming_diff.py**：
- 对比前后两次识别结果文本
- 使用 `difflib.SequenceMatcher` 或编辑距离算法
- 输出变化范围，前端根据变化范围更新显示

**post_corrector.py**：
- 接收 `segment_final` 文本
- 构造提示词：
  ```
  请对以下语音识别结果进行纠错和润色，修正可能的同音字错误、
  标点缺失，保持原意不变。只输出修正后的文本，不要解释。
  
  原文：{text}
  ```
- 异步调用 LLM API，返回修正结果

## 7. 音频传输协议

### 7.1 连接建立

WebSocket 路径：`ws://host:port/ws/voice`

### 7.2 客户端 → 服务端

**控制消息（JSON）：**

```json
{"type": "start", "config": {"lang": "zh", "model": "base", "enable_streaming_correction": true, "enable_post_correction": false, "post_correction_model": "minimax"}}
{"type": "stop"}
```

**音频数据（Binary）：**

前端使用 Web Audio API 录制，通过 `ScriptProcessorNode` 或 `AudioWorklet` 获取 PCM 数据，转换为 **16kHz 16bit 单声道 little-endian**，以 binary frame 发送。

### 7.3 服务端 → 客户端

```json
{"type": "partial", "text": "今天天气", "segment_index": 0, "is_stable": false}
{"type": "partial", "text": "今天天气很好", "segment_index": 0, "is_stable": true}
{"type": "segment_final", "text": "今天天气很好。", "segment_index": 0}
{"type": "post_corrected", "text": "今天天气很好！", "original": "今天天气很好。", "segment_index": 0}
{"type": "complete", "full_text": "今天天气很好！"}
{"type": "error", "message": "模型加载失败"}
```

| 类型 | 说明 |
|---|---|
| `partial` | 流式中间结果，`is_stable=true` 表示该段文本已趋于稳定 |
| `segment_final` | 一段语音结束后的最终识别结果 |
| `post_corrected` | LLM 后处理纠错后的结果（如未开启则不发送） |
| `complete` | 用户主动 stop 或连接关闭时的完整文本汇总 |
| `error` | 错误信息 |

## 8. 流式识别策略

Whisper 按固定长度音频处理，通过以下策略实现准实时流式：

1. **前端采集**：`AudioContext` 以 16kHz 采样，每 300ms 发送一个 binary chunk
2. **后端缓冲**：VAD 检测到有语音时持续累积音频到 `audio_buffer`
3. **触发识别**：
   - 条件 A：缓冲区达到 2 秒音频且检测到语音仍在继续
   - 条件 B：VAD 检测到静音超过 300ms（说话停顿）
   - 条件 C：缓冲区达到 10 秒（强制截断，防止过长）
4. **增量更新**：识别结果与 `last_text` diff，如有变化发送 `partial`
5. **段结束**：条件 B 触发时发送 `segment_final`，清空缓冲区，开始新段

**自然流式纠错**：由于每次识别使用 `condition_on_previous_text=True`，随着上下文增加，前面的文本可能被 Whisper 自身修正。`streaming_diff` 检测这些变化并推送给前端更新。

## 9. 纠错机制

### 9.1 段内流式纠错（`enable_streaming_correction`）

- **开启**：每次增量识别后 diff 对比，变化部分实时推送到前端，前面已显示的文字会被修正
- **关闭**：不发送 `partial` 消息，只在 `segment_final` 时一次性输出整段结果
- 默认：开启

### 9.2 整体后处理纠错（`enable_post_correction`）

- **开启**：`segment_final` 触发后，异步调用 LLM API，返回 `post_corrected` 替换最终文本
- **关闭**：跳过 LLM 调用，直接以 `segment_final` 为最终结果
- 默认：关闭（需配置 API Key）

### 9.3 后处理模型配置

复用现有 `coloop-agent-setting.json` 中的模型定义。Python 服务启动时读取该文件（或环境变量），`post_correction_model` 字段指定使用哪个模型配置（如 `"minimax"`、`"openai"`、`"glm-4-free"`）。

示例环境变量映射：
```bash
# .env
POST_CORRECTION_MODEL=minimax
# 或直接从 coloop-agent-setting.json 读取
SETTING_FILE=../coloop-agent-core/src/main/resources/coloop-agent-setting.json
```

## 10. 前端设计

### 10.1 页面布局（voice-input.html）

```
┌─────────────────────────────────────────┐
│              语音输入器                   │
├─────────────────────────────────────────┤
│                                         │
│           [ 🎤 大圆形按钮 ]               │
│              点击开始录音                 │
│                                         │
│         "今天天气很好..."                │
│           (实时文本, 灰色)                │
│                                         │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐   │
│  │ 最终结果展示区域 (textarea)       │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│         [复制] [清空]                   │
├─────────────────────────────────────────┤
│  [⚙ 设置] 模型: base | 语言: 中文        │
│           实时修正 ✓ | 后处理纠错 ✗      │
└─────────────────────────────────────────┘
```

### 10.2 交互流程

1. 用户点击麦克风按钮 → 请求 `getUserMedia` → 建立 WebSocket → 发送 `start`
2. 录音中：按钮红色脉动，显示音波动画（Web Audio Analyser）
3. 实时文本区：接收 `partial`，灰色斜体显示当前识别进度
4. 一段结束：接收 `segment_final`，文本追加到 textarea
5. 后处理（如开启）：接收 `post_corrected`，替换 textarea 中对应段
6. 点击停止/自动静音检测 → 发送 `stop` → 接收 `complete`
7. 用户点击"复制" → `navigator.clipboard.writeText`

### 10.3 设置面板

- **模型选择**：tiny（39MB）/ base（74MB）/ small（244MB）/ medium（769MB）/ large（1550MB）
  - 首次使用自动下载，显示下载进度
- **语言**：auto / zh / en
- **实时修正**：开关
- **后处理纠错**：开关 + API Key 输入框（如未配置）

## 11. 配置设计

### 11.1 Python 服务配置

`.env` 文件：

```bash
# 服务
HOST=0.0.0.0
PORT=8000

# 模型
WHISPER_MODEL=base
WHISPER_DEVICE=cpu
WHISPER_COMPUTE_TYPE=int8
WHISPER_MODEL_DIR=./models

# 识别
DEFAULT_LANG=zh
ENABLE_STREAMING_CORRECTION=true
ENABLE_POST_CORRECTION=false

# 后处理模型（复用现有配置）
SETTING_FILE=../coloop-agent-core/src/main/resources/coloop-agent-setting.json
POST_CORRECTION_MODEL=minimax
```

### 11.2 与现有配置的兼容

Python `config.py` 读取逻辑：

1. 优先加载 `.env` 中的 `SETTING_FILE` 路径
2. 解析 `coloop-agent-setting.json`，提取指定模型配置
3. 构造 LLM 调用参数（apiBase + apiKey + model）
4. 如 `SETTING_FILE` 不存在，回退到直接环境变量

## 12. 部署与运行

### 12.1 环境要求

- Python 3.10+
- 推荐 8GB+ 内存（CPU 运行 base/small 模型）
- 可选 NVIDIA GPU + CUDA（大幅提升速度）

### 12.2 启动步骤

```bash
cd coloop-agent-voice

# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 首次启动自动下载模型到 ./models/
python main.py

# 打开浏览器
# http://localhost:8000
```

### 12.3 requirements.txt

```
fastapi>=0.110.0
python-socketio>=5.11.0
uvicorn[standard]>=0.29.0
faster-whisper>=1.0.0
webrtcvad>=2.0.10
numpy>=1.26.0
httpx>=0.27.0
python-dotenv>=1.0.0
```

## 13. 预留扩展

- **外部 HTTP 引擎**：`HttpAdapter` 预留接口，通过配置 `http_endpoint` 接入 FunASR / Whisper API / 阿里云 / 讯飞等
- **WebRTC**：后续可扩展 P2P 直连模式，降低服务端延迟
- **多说话人分离**：升级至 Whisper-large-v3 + diarization
