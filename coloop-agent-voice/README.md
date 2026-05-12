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
