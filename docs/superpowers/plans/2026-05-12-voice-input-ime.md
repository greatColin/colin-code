# Voice Input IME Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构语音输入模块为输入法形态，支持 iframe 嵌入和 exe 打包

**Architecture:** 单 HTML 文件 + HTTP REST API。前端使用 MediaRecorder 录音，录音结束后通过 Fetch API 上传音频到后端 `/api/recognize` 端点。后端使用 faster-whisper 识别 + 可选 LLM 纠错。

**Tech Stack:** HTML/CSS/JS (单文件), FastAPI, faster-whisper, MediaRecorder API

---

## 文件结构

```
coloop-agent-voice/
├── static/
│   └── voice-input.html      # 重写：单文件（HTML + CSS + JS）
├── main.py                    # 重写：FastAPI + /api/recognize
├── api/
│   └── recognize.py           # 新建：识别端点逻辑
├── engine/
│   └── whisper_engine.py      # 保留
├── correction/
│   └── post_corrector.py      # 保留
├── audio/
│   └── energy_vad.py          # 保留
└── config.py                  # 精简
```

---

### Task 1: 创建 /api/recognize 端点

**Files:**
- Create: `coloop-agent-voice/api/__init__.py`
- Create: `coloop-agent-voice/api/recognize.py`
- Modify: `coloop-agent-voice/main.py`

- [ ] **Step 1: 创建 api 包和识别端点**

```python
# coloop-agent-voice/api/__init__.py
```

```python
# coloop-agent-voice/api/recognize.py
import time
from fastapi import APIRouter, UploadFile, File, Form
from typing import Optional

router = APIRouter()


@router.post("/api/recognize")
async def recognize(
    audio: UploadFile = File(...),
    enable_correction: bool = Form(False),
):
    """
    接收音频文件，返回识别结果。

    Request:
      - audio: 音频文件 (webm/opus 或 wav)
      - enable_correction: 是否启用 LLM 纠错

    Response:
      {
        "text": "最终识别结果",
        "raw_text": "原始识别结果",
        "corrected": false,
        "duration_ms": 1234
      }
    """
    # TODO: 实际识别逻辑在 Task 3 实现
    return {
        "text": "placeholder",
        "raw_text": "placeholder",
        "corrected": False,
        "duration_ms": 0,
    }
```

- [ ] **Step 2: 修改 main.py 注册路由**

```python
# coloop-agent-voice/main.py
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from api.recognize import router as recognize_router

app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")
app.include_router(recognize_router)


@app.get("/api/config")
async def get_config():
    return {
        "enableCorrection": False,
        "postAction": "none",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

- [ ] **Step 3: 验证端点可用**

Run: `cd coloop-agent-voice && python main.py`
Expected: 服务启动在 http://localhost:8000

访问 http://localhost:8000/docs 可看到 Swagger 文档，包含 `/api/recognize` 端点。

- [ ] **Step 4: 提交**

```bash
git add coloop-agent-voice/api/ coloop-agent-voice/main.py
git commit -m "feat(voice): 添加 /api/recognize 端点骨架"
```

---

### Task 2: 实现前端输入法界面

**Files:**
- Rewrite: `coloop-agent-voice/static/voice-input.html`

- [ ] **Step 1: 重写 voice-input.html**

完全重写为输入法形态。单文件，内联 CSS + JS。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>语音输入</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            background: transparent;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 8px;
            min-height: 100vh;
            justify-content: flex-end;
        }

        /* 气泡 */
        .bubble {
            background: rgba(30, 30, 30, 0.95);
            color: #fff;
            padding: 10px 16px;
            border-radius: 12px;
            font-size: 14px;
            line-height: 1.5;
            max-width: 360px;
            word-break: break-word;
            margin-bottom: 8px;
            opacity: 0;
            transform: translateY(8px);
            transition: opacity 0.25s, transform 0.25s;
            pointer-events: none;
        }

        .bubble.visible {
            opacity: 1;
            transform: translateY(0);
            pointer-events: auto;
        }

        .bubble.status {
            color: rgba(255, 255, 255, 0.7);
            font-size: 13px;
        }

        .bubble.error {
            background: rgba(200, 50, 50, 0.9);
        }

        /* 主栏 */
        .bar {
            display: flex;
            align-items: center;
            background: rgba(30, 30, 30, 0.95);
            border-radius: 24px;
            padding: 6px 12px;
            gap: 10px;
            width: 360px;
            backdrop-filter: blur(20px);
        }

        /* 麦克风按钮 */
        .mic-btn {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: none;
            background: rgba(255, 255, 255, 0.15);
            color: #fff;
            font-size: 20px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            transition: background 0.2s;
        }

        .mic-btn:hover {
            background: rgba(255, 255, 255, 0.25);
        }

        .mic-btn.recording {
            background: rgba(255, 59, 48, 0.4);
            animation: pulse 1.5s ease-in-out infinite;
        }

        @keyframes pulse {
            0%, 100% { transform: scale(1); }
            50% { transform: scale(1.08); }
        }

        /* 声纹 */
        .waveform {
            flex: 1;
            height: 32px;
            border-radius: 8px;
            background: rgba(255, 255, 255, 0.05);
        }

        /* 设置按钮 */
        .settings-btn {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            border: none;
            background: transparent;
            color: rgba(255, 255, 255, 0.5);
            font-size: 18px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            transition: color 0.2s;
        }

        .settings-btn:hover {
            color: rgba(255, 255, 255, 0.8);
        }

        /* 设置面板 */
        .settings-panel {
            display: none;
            background: rgba(30, 30, 30, 0.95);
            border-radius: 0 0 16px 16px;
            padding: 12px 16px;
            width: 360px;
            backdrop-filter: blur(20px);
        }

        .settings-panel.open {
            display: block;
        }

        .setting-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 0;
            color: rgba(255, 255, 255, 0.8);
            font-size: 13px;
        }

        .setting-row + .setting-row {
            border-top: 1px solid rgba(255, 255, 255, 0.08);
        }

        /* 开关 */
        .toggle {
            width: 40px;
            height: 22px;
            border-radius: 11px;
            background: rgba(255, 255, 255, 0.2);
            position: relative;
            cursor: pointer;
            transition: background 0.2s;
            flex-shrink: 0;
        }

        .toggle.active {
            background: #34c759;
        }

        .toggle::after {
            content: '';
            position: absolute;
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: #fff;
            top: 2px;
            left: 2px;
            transition: transform 0.2s;
        }

        .toggle.active::after {
            transform: translateX(18px);
        }

        /* 单选 */
        .radio-group {
            display: flex;
            gap: 12px;
        }

        .radio-option {
            display: flex;
            align-items: center;
            gap: 6px;
            cursor: pointer;
            font-size: 13px;
            color: rgba(255, 255, 255, 0.7);
        }

        .radio-dot {
            width: 16px;
            height: 16px;
            border-radius: 50%;
            border: 2px solid rgba(255, 255, 255, 0.3);
            position: relative;
            flex-shrink: 0;
        }

        .radio-dot.active {
            border-color: #34c759;
        }

        .radio-dot.active::after {
            content: '';
            position: absolute;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #34c759;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
        }

        /* 旋转动画 */
        @keyframes spin {
            to { transform: rotate(360deg); }
        }

        .spinner {
            display: inline-block;
            width: 14px;
            height: 14px;
            border: 2px solid rgba(255, 255, 255, 0.3);
            border-top-color: #fff;
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
            vertical-align: middle;
            margin-right: 6px;
        }
    </style>
</head>
<body>
    <div class="bubble" id="bubble"></div>

    <div class="bar">
        <button class="mic-btn" id="micBtn">🎤</button>
        <canvas class="waveform" id="waveform"></canvas>
        <button class="settings-btn" id="settingsBtn">⚙</button>
    </div>

    <div class="settings-panel" id="settingsPanel">
        <div class="setting-row">
            <span>LLM 纠错</span>
            <div class="toggle" id="correctionToggle"></div>
        </div>
        <div class="setting-row">
            <span>识别后</span>
            <div class="radio-group">
                <div class="radio-option" data-value="none">
                    <div class="radio-dot active" data-group="postAction" data-value="none"></div>
                    <span>无操作</span>
                </div>
                <div class="radio-option" data-value="copy">
                    <div class="radio-dot" data-group="postAction" data-value="copy"></div>
                    <span>复制</span>
                </div>
            </div>
        </div>
    </div>

    <script>
    (function() {
        // DOM
        const micBtn = document.getElementById('micBtn');
        const waveformCanvas = document.getElementById('waveform');
        const settingsBtn = document.getElementById('settingsBtn');
        const settingsPanel = document.getElementById('settingsPanel');
        const bubbleEl = document.getElementById('bubble');
        const correctionToggle = document.getElementById('correctionToggle');
        const waveformCtx = waveformCanvas.getContext('2d');

        // State
        let isRecording = false;
        let mediaRecorder = null;
        let audioChunks = [];
        let audioContext = null;
        let analyser = null;
        let animationId = null;
        let envelopeHistory = [];
        const MAX_HISTORY = 100;

        // 设置
        let enableCorrection = false;
        let postAction = 'none'; // 'none' | 'copy'

        // --- 气泡 ---
        function showBubble(text, type) {
            bubbleEl.textContent = text;
            bubbleEl.className = 'bubble visible';
            if (type === 'status') bubbleEl.classList.add('status');
            else if (type === 'error') bubbleEl.classList.add('error');
        }

        function hideBubble() {
            bubbleEl.className = 'bubble';
        }

        // --- 声纹 ---
        function resizeCanvas() {
            const rect = waveformCanvas.getBoundingClientRect();
            waveformCanvas.width = rect.width * window.devicePixelRatio;
            waveformCanvas.height = rect.height * window.devicePixelRatio;
            waveformCtx.scale(window.devicePixelRatio, window.devicePixelRatio);
        }

        function drawWaveform() {
            if (!analyser) return;

            const bufferLength = analyser.frequencyBinCount;
            const dataArray = new Uint8Array(bufferLength);
            analyser.getByteTimeDomainData(dataArray);

            let peak = 0;
            for (let i = 0; i < bufferLength; i++) {
                const v = Math.abs(dataArray[i] - 128) / 128;
                if (v > peak) peak = v;
            }

            envelopeHistory.push(peak);
            if (envelopeHistory.length > MAX_HISTORY) envelopeHistory.shift();

            const rect = waveformCanvas.getBoundingClientRect();
            const width = rect.width;
            const height = rect.height;
            const centerY = height / 2;

            waveformCtx.clearRect(0, 0, width, height);

            // 填充
            waveformCtx.beginPath();
            waveformCtx.moveTo(0, centerY);
            for (let i = 0; i < envelopeHistory.length; i++) {
                const x = (i / (MAX_HISTORY - 1)) * width;
                const y = centerY - envelopeHistory[i] * centerY * 0.8;
                waveformCtx.lineTo(x, y);
            }
            for (let i = envelopeHistory.length - 1; i >= 0; i--) {
                const x = (i / (MAX_HISTORY - 1)) * width;
                const y = centerY + envelopeHistory[i] * centerY * 0.8;
                waveformCtx.lineTo(x, y);
            }
            waveformCtx.closePath();
            waveformCtx.fillStyle = 'rgba(255, 255, 255, 0.15)';
            waveformCtx.fill();

            // 线条
            waveformCtx.beginPath();
            for (let i = 0; i < envelopeHistory.length; i++) {
                const x = (i / (MAX_HISTORY - 1)) * width;
                const y = centerY - envelopeHistory[i] * centerY * 0.8;
                if (i === 0) waveformCtx.moveTo(x, y);
                else waveformCtx.lineTo(x, y);
            }
            waveformCtx.strokeStyle = 'rgba(255, 255, 255, 0.6)';
            waveformCtx.lineWidth = 1.5;
            waveformCtx.lineJoin = 'round';
            waveformCtx.stroke();

            animationId = requestAnimationFrame(drawWaveform);
        }

        // --- 录音 ---
        async function startRecording() {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({
                    audio: {
                        sampleRate: 16000,
                        channelCount: 1,
                        echoCancellation: true,
                        noiseSuppression: true,
                    }
                });

                // 声纹可视化
                audioContext = new AudioContext({ sampleRate: 16000 });
                const source = audioContext.createMediaStreamSource(stream);
                analyser = audioContext.createAnalyser();
                analyser.fftSize = 256;
                source.connect(analyser);

                resizeCanvas();
                envelopeHistory = [];
                drawWaveform();

                // MediaRecorder
                mediaRecorder = new MediaRecorder(stream, {
                    mimeType: 'audio/webm;codecs=opus'
                });
                audioChunks = [];

                mediaRecorder.ondataavailable = (e) => {
                    if (e.data.size > 0) audioChunks.push(e.data);
                };

                mediaRecorder.onstop = async () => {
                    const blob = new Blob(audioChunks, { type: 'audio/webm' });
                    stream.getTracks().forEach(t => t.stop());
                    if (audioContext) {
                        audioContext.close();
                        audioContext = null;
                    }
                    cancelAnimationFrame(animationId);
                    clearCanvas();
                    await recognizeAudio(blob);
                };

                mediaRecorder.start(100); // 每100ms收集一次数据
                isRecording = true;
                micBtn.classList.add('recording');
                micBtn.textContent = '⏹';
                hideBubble();

            } catch (err) {
                console.error('录音失败:', err);
                showBubble('无法访问麦克风', 'error');
            }
        }

        function stopRecording() {
            if (mediaRecorder && mediaRecorder.state !== 'inactive') {
                mediaRecorder.stop();
            }
            isRecording = false;
            micBtn.classList.remove('recording');
            micBtn.textContent = '🎤';
        }

        function clearCanvas() {
            const rect = waveformCanvas.getBoundingClientRect();
            waveformCtx.clearRect(0, 0, rect.width, rect.height);
        }

        // --- 识别 ---
        async function recognizeAudio(audioBlob) {
            showBubble('<span class="spinner"></span>识别中...', 'status');

            const formData = new FormData();
            formData.append('audio', audioBlob, 'recording.webm');
            formData.append('enable_correction', enableCorrection);

            try {
                const resp = await fetch('/api/recognize', {
                    method: 'POST',
                    body: formData,
                });

                if (!resp.ok) {
                    throw new Error(`HTTP ${resp.status}`);
                }

                const data = await resp.json();

                if (data.corrected) {
                    showBubble('<span class="spinner"></span>纠错中...', 'status');
                    await new Promise(r => setTimeout(r, 200)); // 短暂显示纠错状态
                }

                if (data.text) {
                    showBubble(data.text);

                    // 识别后操作
                    if (postAction === 'copy') {
                        try {
                            await navigator.clipboard.writeText(data.text);
                        } catch (e) {
                            console.warn('复制失败:', e);
                        }
                    }

                    // 通知父页面
                    window.parent.postMessage({
                        type: 'voice-result',
                        text: data.text,
                        rawText: data.raw_text,
                        corrected: data.corrected,
                    }, '*');
                } else {
                    hideBubble();
                }

            } catch (err) {
                console.error('识别失败:', err);
                showBubble('识别失败: ' + err.message, 'error');
            }
        }

        // --- 事件绑定 ---
        micBtn.addEventListener('click', () => {
            if (isRecording) stopRecording();
            else startRecording();
        });

        settingsBtn.addEventListener('click', () => {
            settingsPanel.classList.toggle('open');
        });

        correctionToggle.addEventListener('click', () => {
            correctionToggle.classList.toggle('active');
            enableCorrection = correctionToggle.classList.contains('active');
        });

        // 单选组
        document.querySelectorAll('.radio-dot').forEach(dot => {
            dot.addEventListener('click', () => {
                const group = dot.dataset.group;
                const value = dot.dataset.value;
                document.querySelectorAll(`.radio-dot[data-group="${group}"]`).forEach(d => {
                    d.classList.remove('active');
                });
                dot.classList.add('active');
                if (group === 'postAction') postAction = value;
            });
        });

        // 窗口大小变化时重绘
        window.addEventListener('resize', () => {
            if (analyser) {
                resizeCanvas();
            }
        });

        // 监听父页面消息
        window.addEventListener('message', (event) => {
            if (event.data.type === 'voice-start') {
                if (!isRecording) startRecording();
            } else if (event.data.type === 'voice-stop') {
                if (isRecording) stopRecording();
            }
        });

        // 初始化
        resizeCanvas();
    })();
    </script>
</body>
</html>
```

- [ ] **Step 2: 验证页面加载**

Run: `cd coloop-agent-voice && python main.py`
Expected: 访问 http://localhost:8000/static/voice-input.html 显示输入法界面

- [ ] **Step 3: 提交**

```bash
git add coloop-agent-voice/static/voice-input.html
git commit -m "feat(voice): 重写前端为输入法形态"
```

---

### Task 3: 实现识别端点逻辑

**Files:**
- Modify: `coloop-agent-voice/api/recognize.py`
- Modify: `coloop-agent-voice/main.py`

- [ ] **Step 1: 修改 main.py 注入依赖**

```python
# coloop-agent-voice/main.py
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from api.recognize import router as recognize_router, set_engine
from factory import VoiceFactory

DEFAULT_SETTING_FILE = "../coloop-agent-core/src/main/resources/coloop-agent-setting.json"
factory = VoiceFactory(setting_file=DEFAULT_SETTING_FILE)

_transcription_strategy = None
_correction_strategy = None


async def init_engines():
    global _transcription_strategy, _correction_strategy
    loop = asyncio.get_event_loop()
    _transcription_strategy = await loop.run_in_executor(None, factory.create_transcription)
    _correction_strategy = factory.create_correction()
    set_engine(_transcription_strategy, _correction_strategy)


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("[startup] loading whisper model...")
    await init_engines()
    print("[startup] ready")
    yield


app = FastAPI(lifespan=lifespan)
app.mount("/static", StaticFiles(directory="static"), name="static")
app.include_router(recognize_router)


@app.get("/api/config")
async def get_config():
    return {
        "enableCorrection": False,
        "postAction": "none",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=factory.config.host, port=factory.config.port)
```

- [ ] **Step 2: 实现识别端点**

```python
# coloop-agent-voice/api/recognize.py
import time
import tempfile
import os
from fastapi import APIRouter, UploadFile, File, Form
from typing import Optional

router = APIRouter()

_transcription = None
_correction = None


def set_engine(transcription, correction):
    global _transcription, _correction
    _transcription = transcription
    _correction = correction


@router.post("/api/recognize")
async def recognize(
    audio: UploadFile = File(...),
    enable_correction: bool = Form(False),
):
    """
    接收音频文件，返回识别结果。
    """
    if _transcription is None:
        return {"text": "", "raw_text": "", "corrected": False, "duration_ms": 0, "error": "引擎未初始化"}

    start_time = time.time()

    # 保存临时文件
    suffix = ".webm" if "webm" in (audio.content_type or "") else ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await audio.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # 读取音频并转为 PCM（如果需要）
        # faster-whisper 可以直接处理多种格式
        with open(tmp_path, "rb") as f:
            audio_bytes = f.read()

        # 识别
        raw_text = _transcription.transcribe(audio_bytes, language="zh")
        duration_ms = int((time.time() - start_time) * 1000)

        if not raw_text:
            return {"text": "", "raw_text": "", "corrected": False, "duration_ms": duration_ms}

        # 纠错
        text = raw_text
        corrected = False
        if enable_correction and _correction:
            try:
                corrected_text = await _correction.correct(raw_text)
                if corrected_text != raw_text:
                    text = corrected_text
                    corrected = True
            except Exception as e:
                print(f"[recognize] correction error: {e}")

        return {
            "text": text,
            "raw_text": raw_text,
            "corrected": corrected,
            "duration_ms": duration_ms,
        }

    finally:
        os.unlink(tmp_path)
```

- [ ] **Step 3: 验证识别功能**

Run: `cd coloop-agent-voice && python main.py`
打开 http://localhost:8000/static/voice-input.html，录音后检查是否返回识别结果。

- [ ] **Step 4: 提交**

```bash
git add coloop-agent-voice/api/ coloop-agent-voice/main.py
git commit -m "feat(voice): 实现识别端点逻辑"
```

---

### Task 4: 精简配置

**Files:**
- Modify: `coloop-agent-voice/config.py`

- [ ] **Step 1: 精简 VoiceConfig**

```python
# coloop-agent-voice/config.py
import json
import re
import os
from pathlib import Path
from typing import Any, Dict, Optional


class VoiceConfig:
    """Voice module configuration."""

    DEFAULTS = {
        "host": "0.0.0.0",
        "port": 8000,
        "language": "zh",
    }

    def __init__(self, setting_file: str = None):
        self._raw: Dict[str, Any] = {}
        self._models: Dict[str, Any] = {}
        if setting_file:
            self._load(setting_file)
        self._voice = self._raw.get("voice", {})

    def _load(self, path: str):
        p = Path(path)
        if not p.exists():
            print(f"[VoiceConfig] setting file not found: {path}, using defaults")
            return
        with open(p, "r", encoding="utf-8") as f:
            raw_text = f.read()
        raw_text = self._expand_env_vars(raw_text)
        self._raw = json.loads(raw_text)
        self._models = self._raw.get("models", {})

    @staticmethod
    def _expand_env_vars(text: str) -> str:
        def replacer(match):
            var_name = match.group(1)
            return os.environ.get(var_name, match.group(0))
        return re.sub(r"\$\{(\w+)\}", replacer, text)

    def get(self, key: str) -> Any:
        return self._voice.get(key, self.DEFAULTS.get(key))

    def get_transcription_strategy_name(self) -> str:
        return self._voice.get("transcription", {}).get("strategy", "local_whisper")

    def get_transcription_params(self, strategy_name: str) -> dict:
        return self._voice.get("transcription", {}).get("strategies", {}).get(strategy_name, {})

    def get_correction_strategy_name(self) -> str:
        return self._voice.get("correction", {}).get("strategy", "none")

    def get_correction_params(self, strategy_name: str) -> dict:
        return self._voice.get("correction", {}).get("strategies", {}).get(strategy_name, {})

    def get_model_config(self, name: str) -> Optional[Dict[str, Any]]:
        return self._models.get(name)

    @property
    def host(self) -> str:
        return self.get("host")

    @property
    def port(self) -> int:
        return self.get("port")
```

- [ ] **Step 2: 提交**

```bash
git add coloop-agent-voice/config.py
git commit -m "refactor(voice): 精简配置，移除无用选项"
```

---

### Task 5: 清理无用文件

**Files:**
- Delete: `coloop-agent-voice/static/audio-processor.js`
- Delete: `coloop-agent-voice/static/voice-input.js`
- Delete: `coloop-agent-voice/session/voice_session.py`
- Delete: `coloop-agent-voice/engine/websocket_adapter.py`
- Delete: `coloop-agent-voice/engine/http_adapter.py`
- Delete: `coloop-agent-voice/correction/streaming_diff.py`
- Delete: `coloop-agent-voice/audio/vad_processor.py`

- [ ] **Step 1: 删除无用文件**

```bash
cd coloop-agent-voice
rm -f static/audio-processor.js static/voice-input.js
rm -f session/voice_session.py
rm -f engine/websocket_adapter.py engine/http_adapter.py
rm -f correction/streaming_diff.py
rm -f audio/vad_processor.py
```

- [ ] **Step 2: 验证服务正常**

Run: `python main.py`
Expected: 服务正常启动，无导入错误

- [ ] **Step 3: 提交**

```bash
git add -A coloop-agent-voice/
git commit -m "chore(voice): 清理无用文件"
```

---

### Task 6: 更新 README

**Files:**
- Modify: `coloop-agent-voice/README.md`

- [ ] **Step 1: 更新 README**

```markdown
# coloop-agent-voice

语音输入服务。基于 faster-whisper，支持一次性识别与 LLM 纠错。

## 功能

- 一次性识别：录音完成后自动识别，准确率更高
- LLM 纠错：可选的后处理纠错
- 输入法形态：长条状界面，可嵌入 iframe
- 本地模型：默认 faster-whisper

## 环境要求

- Python 3.10+
- 推荐 8GB+ 内存（CPU 运行 base/small 模型）
- 可选 NVIDIA GPU + CUDA

## 安装

```bash
cd coloop-agent-voice
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## 运行

```bash
python main.py
# 打开浏览器访问 http://localhost:8000/static/voice-input.html
```

首次启动会自动下载 Whisper 模型到 `./models/`。

## 使用

1. 点击麦克风按钮开始录音
2. 再次点击停止录音
3. 等待识别完成，结果自动显示
4. 可在设置中开启 LLM 纠错

## 嵌入

```html
<iframe src="http://localhost:8000/static/voice-input.html" width="380" height="80"></iframe>

<script>
window.addEventListener('message', (event) => {
  if (event.data.type === 'voice-result') {
    console.log('识别结果:', event.data.text);
  }
});
</script>
```
```

- [ ] **Step 2: 提交**

```bash
git add coloop-agent-voice/README.md
git commit -m "docs(voice): 更新 README"
```

---

## 验证清单

- [ ] 页面加载后显示长条状输入法界面
- [ ] 点击麦克风按钮开始录音，声纹实时显示
- [ ] 再次点击停止录音，自动触发识别
- [ ] 气泡显示"识别中..."，完成后显示结果
- [ ] 如果启用纠错，气泡显示"纠错中..."
- [ ] 识别结果可通过 postMessage 发送到父页面
- [ ] 设置面板可切换 LLM 纠错和识别后操作
- [ ] 服务可正常启动，无导入错误
