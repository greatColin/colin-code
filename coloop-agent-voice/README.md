# coloop-agent-voice

独立语音输入服务。基于 faster-whisper，支持流式语音识别与可配置纠错。

## 功能

- 流式语音识别：边说边出文字
- 可配置纠错：实时修正 + LLM 后处理，独立开关
- 本地模型：默认 faster-whisper，支持多尺寸模型
- 单页前端：Apple 液态玻璃风格，麦克风一键录音

## 环境要求

- Python 3.10+
- 推荐 8GB+ 内存（CPU 运行 base/small 模型）
- 可选 NVIDIA GPU + CUDA

## 安装依赖

```bash
cd coloop-agent-voice
pip install -r requirements.txt
```

## 配置

复用上层 `coloop-agent-core/src/main/resources/coloop-agent-setting.json`：

```jsonc
{
  "voice": {
    "host": "0.0.0.0",
    "port": 8000,
    "language": "zh",
    "recognitionMode": "realtime",
    "enableStreamingCorrection": true,
    "enablePostCorrection": true,
    "transcription": { "strategy": "local_whisper" },
    "correction": { "strategy": "none" }
  }
}
```

后处理纠错复用 `coloop-agent-setting.json` 中的模型配置。

## 运行

```bash
python main.py
# 打开浏览器访问 http://localhost:8000/static/voice-input.html
```

首次启动会自动下载 Whisper 模型到 `./models/`。

## 使用

1. 点击麦克风按钮开始录音
2. 实时文本显示在上方灰色区域
3. 一段话结束后结果追加到下方白色区域
4. 点击「复制」复制全部结果
