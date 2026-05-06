# Voice Module Settings Enhancement Design

Date: 2026-05-06

## Overview

Enhance the voice module's settings panel with three new configuration areas:
1. Recognition mode selection (3 modes)
2. Correction switches (2 independent toggles, both default ON)
3. End action selection (3 options, with coloop WS integration)

## 1. Recognition Mode

### Modes

| Mode | ID | Description |
|------|----|-------------|
| Real-time | `realtime` | Current behavior: VAD segments audio, each segment transcribed independently with live preview. **Default.** |
| Real-time + Final | `realtime_final` | Same live preview during recording, but all raw audio segments are stored. On stop, all segments are concatenated and re-transcribed for maximum accuracy. The final result replaces preview text. |
| Final only | `final_only` | No preview during recording (only waveform + status). All audio accumulated, transcribed on stop. Uses `_transcribe_long_audio` for chunking if > 30s. |

### Motivation

One-shot transcription improves accuracy because:
- Whisper sees full context across the entire recording (`condition_on_previous_text` works better)
- Avoids VAD mis-cuts that split words mid-way
- Punctuation and sentence boundaries are more coherent

### Backend Changes

**`VoiceSession.__init__`** — add `recognition_mode: str = "realtime"` parameter.

**`VoiceSession.feed_audio`** — branch on mode:
- `realtime`: current behavior (VAD → segment → transcribe → preview)
- `realtime_final`: same as `realtime` for preview, but also store raw segment bytes in `self._raw_segments: list[bytes]`
- `final_only`: VAD still runs for UI feedback (emit `vad_status` events), but no transcription. Raw audio accumulated in `self._raw_segments`

**`VoiceSession.stop`** — branch on mode:
- `realtime`: current behavior (flush → finalize → complete)
- `realtime_final`: concatenate `self._raw_segments` → re-transcribe → emit `final_text` event → complete
- `final_only`: pass accumulated audio to `_transcribe_long_audio` → emit result → complete

**New helper: `_transcribe_long_audio(audio_bytes) -> str`** — if audio <= 30s, transcribe directly. If > 30s, splits into chunks at silence boundaries (low RMS frames) near the 25s mark (no overlap needed — Whisper handles long audio internally, chunking is for memory/latency control). Transcribes each chunk, joins results. Used by both `realtime_final` (on stop) and `final_only`.

### Frontend Changes

- Settings panel: 3 radio buttons for recognition mode
- `final_only` mode: hide `#realtimeText` area during recording, show only waveform + status
- `realtime_final` mode: on `complete` event, if `final_text` is present, replace `resultArea` content with final text
- New `vad_status` event handler (optional): show "检测到语音..." / "等待语音..." status during `final_only` mode

### Config

```json
"voice": {
  "recognitionMode": "realtime"
}
```

Frontend sends `recognition_mode` in the `start` event config. Backend uses it to configure `VoiceSession`.

## 2. Correction Switches

### Current State

- `enableStreamingCorrection` (real-time diff): default `True`
- Post-processing correction strategy: default `"none"` (disabled)

### Changes

- Both switches default to **ON**
- `correction.strategy` default changes from `"none"` to `"llm"`
- `VoiceConfig.DEFAULTS` updated:
  ```python
  DEFAULTS = {
      "host": "0.0.0.0",
      "port": 8000,
      "language": "zh",
      "enableStreamingCorrection": True,
      "enablePostCorrection": True,
  }
  ```
- Frontend settings panel: both toggles have `active` class by default
- Frontend `start` event sends both `enable_streaming_correction` and `enable_post_correction`
- Backend `on_start` handler: if `enable_post_correction` is `False`, force correction strategy to `NoOpCorrectionStrategy` regardless of config

### Config

```json
"voice": {
  "enableStreamingCorrection": true,
  "correction": {
    "strategy": "llm",
    ...
  }
}
```

## 3. End Action

### Options

| Action | ID | Description |
|--------|----|-------------|
| No action | `none` | User manually clicks Copy button. **Default.** |
| Auto copy | `auto_copy` | Automatically copy to clipboard on recording stop. |
| Send to coloop | `send_coloop` | Send transcribed text to coloop server via WebSocket. Only available when WS connection is active. |

### coloop WebSocket Integration

**Connection:**
- Voice frontend opens a native `WebSocket` connection to coloop server's `/ws/agent` endpoint
- WS URL read from config: `voice.coloopServer.wsUrl` (default: `ws://localhost:8080/ws/agent`)
- Connection established on page load (or on first recording start)
- Auto-reconnect on disconnect (3s delay, same as coloop's chat.js pattern)

**Sending:**
- On recording stop (after `complete` event), if end action is `send_coloop`:
  ```javascript
  coloopSocket.send(JSON.stringify({ action: "chat", message: fullText }));
  ```
- Status shows "已发送到 coloop"

**Connection status indicator:**
- Settings panel shows connection status next to "发送到 coloop" option
- Green dot + "已连接" when WS is open
- Gray dot + "未连接" when WS is closed
- Option is disabled (grayed out) when WS is not connected

**coloop server side:**
- No changes needed. The `{ action: "chat", message: text }` format is already handled by `AgentWebSocketHandler`
- The voice input appears as a regular user message in the chat

### Config

```json
"voice": {
  "coloopServer": {
    "wsUrl": "ws://localhost:8080/ws/agent"
  }
}
```

### Frontend Changes

- Settings panel: 3 radio buttons for end action
- New `ColoopConnection` class managing the WS client lifecycle
- Status indicator in settings panel
- `complete` event handler extended: based on end action, auto-copy or auto-send

## Settings Panel Layout

```
⚙ 设置
│
├─ 识别模式
│   ○ 实时识别（默认）
│   ○ 实时 + 最终识别
│   ○ 仅最终识别
│
├─ 纠错
│   ☑ 实时修正
│   ☑ 后处理纠错
│
└─ 结束操作
    ○ 无操作（默认）
    ○ 自动复制到剪贴板
    ○ 发送到 coloop  [● 已连接]
```

## Files to Modify

| File | Changes |
|------|---------|
| `coloop-agent-setting.json` | Add `recognitionMode`, `coloopServer.wsUrl`, change `correction.strategy` default to `"llm"` |
| `config.py` | Add defaults for new config keys |
| `main.py` | Pass new config to VoiceSession, handle `recognition_mode` and `enable_post_correction` from frontend |
| `session/voice_session.py` | Add `recognition_mode` parameter, branch logic in `feed_audio`/`stop`, add `_transcribe_long_audio` helper |
| `static/voice-input.html` | Add recognition mode radio buttons, end action radio buttons, WS status indicator |
| `static/voice-input.js` | Add `ColoopConnection` class, handle new settings, extend `complete` handler for end actions |

## Testing

- Unit tests for `VoiceSession` with each recognition mode
- Unit tests for `_transcribe_long_audio` chunking logic
- Integration test: voice → coloop WS message delivery
- Frontend manual test: settings persistence, mode switching, WS connection lifecycle
