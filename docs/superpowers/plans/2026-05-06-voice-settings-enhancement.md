# Voice Module Settings Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add recognition mode selection, configurable correction defaults, and end-action options (clipboard/coloop WS) to the voice module settings panel.

**Architecture:** VoiceSession gains a `recognition_mode` parameter that branches audio processing logic. Frontend settings panel expands with radio buttons and toggles. A new `ColoopConnection` class manages a native WebSocket client to coloop-server's `/ws/agent` endpoint.

**Tech Stack:** Python (FastAPI, python-socketio), vanilla JS (Socket.IO client, native WebSocket), HTML/CSS

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `coloop-agent-core/.../coloop-agent-setting.json` | Modify | Add `recognitionMode`, `coloopServer.wsUrl`, change correction default |
| `coloop-agent-voice/config.py` | Modify | New defaults, `get_coloop_ws_url()` method |
| `coloop-agent-voice/session/voice_session.py` | Modify | `recognition_mode` parameter, branch logic, `_transcribe_long_audio` |
| `coloop-agent-voice/main.py` | Modify | Pass new config to VoiceSession, handle `enable_post_correction` |
| `coloop-agent-voice/static/voice-input.html` | Modify | New settings UI (radio buttons, toggles, WS status) |
| `coloop-agent-voice/static/voice-input.js` | Modify | Mode logic, ColoopConnection class, end action handler |
| `coloop-agent-voice/tests/test_config.py` | Modify | Tests for new config keys |
| `coloop-agent-voice/tests/test_voice_session.py` | Modify | Tests for each recognition mode |

---

### Task 1: Update Config System

**Files:**
- Modify: `coloop-agent-voice/config.py`
- Modify: `coloop-agent-voice/tests/test_config.py`
- Modify: `coloop-agent-core/src/main/resources/coloop-agent-setting.json`

- [ ] **Step 1: Write tests for new config defaults**

Add to `tests/test_config.py`:

```python
def test_default_recognition_mode():
    config = VoiceConfig()
    assert config.get("recognitionMode") == "realtime"


def test_default_coloop_ws_url():
    config = VoiceConfig()
    assert config.get_coloop_ws_url() == "ws://localhost:8080/ws/agent"


def test_coloop_ws_url_from_config():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({
                "voice": {
                    "coloopServer": {"wsUrl": "ws://192.168.1.100:9090/ws/agent"}
                }
            }, f)
        config = VoiceConfig(setting_file=path)
        assert config.get_coloop_ws_url() == "ws://192.168.1.100:9090/ws/agent"


def test_default_enable_post_correction():
    config = VoiceConfig()
    assert config.get("enablePostCorrection") is True
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd coloop-agent-voice && python -m pytest tests/test_config.py::test_default_recognition_mode tests/test_config.py::test_default_coloop_ws_url tests/test_config.py::test_coloop_ws_url_from_config tests/test_config.py::test_default_enable_post_correction -v`

Expected: FAIL — `get_coloop_ws_url` method doesn't exist, `recognitionMode` and `enablePostCorrection` defaults missing.

- [ ] **Step 3: Update VoiceConfig**

In `config.py`, update `DEFAULTS` and add `get_coloop_ws_url()`:

```python
DEFAULTS = {
    "host": "0.0.0.0",
    "port": 8000,
    "language": "zh",
    "enableStreamingCorrection": True,
    "enablePostCorrection": True,
    "recognitionMode": "realtime",
}
```

Add method after `get_correction_params`:

```python
def get_coloop_ws_url(self) -> str:
    return self._voice.get("coloopServer", {}).get(
        "wsUrl", "ws://localhost:8080/ws/agent"
    )
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd coloop-agent-voice && python -m pytest tests/test_config.py -v`

Expected: All PASS.

- [ ] **Step 5: Update coloop-agent-setting.json**

In `coloop-agent-core/src/main/resources/coloop-agent-setting.json`, update the `voice` section:

```json
"voice": {
    "transcription": {
        "strategy": "local_whisper",
        "strategies": {
            "local_whisper": {
                "model": "base",
                "device": "cpu",
                "computeType": "int8",
                "modelDir": "./models"
            },
            "http_api": {
                "apiUrl": "",
                "apiKey": "",
                "model": ""
            },
            "websocket": {
                "wsUrl": "",
                "apiKey": ""
            }
        }
    },
    "correction": {
        "strategy": "llm",
        "strategies": {
            "llm": {
                "model": "minimax"
            },
            "none": {}
        }
    },
    "language": "zh",
    "enableStreamingCorrection": true,
    "enablePostCorrection": true,
    "recognitionMode": "realtime",
    "coloopServer": {
        "wsUrl": "ws://localhost:8080/ws/agent"
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add coloop-agent-voice/config.py coloop-agent-voice/tests/test_config.py coloop-agent-core/src/main/resources/coloop-agent-setting.json
git commit -m "feat(voice): add config keys for recognition mode, post-correction, and coloop WS URL"
```

---

### Task 2: VoiceSession — Refactor Existing Behavior as `realtime` Mode

**Files:**
- Modify: `coloop-agent-voice/session/voice_session.py`
- Modify: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write test for recognition_mode parameter**

Add to `tests/test_voice_session.py`:

```python
@pytest.mark.asyncio
async def test_session_default_recognition_mode():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
    )
    assert session.recognition_mode == "realtime"


@pytest.mark.asyncio
async def test_session_recognition_mode_param():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
    )
    assert session.recognition_mode == "final_only"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py::test_session_default_recognition_mode tests/test_voice_session.py::test_session_recognition_mode_param -v`

Expected: FAIL — `recognition_mode` parameter not accepted.

- [ ] **Step 3: Add recognition_mode to VoiceSession.__init__**

In `session/voice_session.py`, update `__init__`:

```python
def __init__(
    self,
    transcription_strategy: TranscriptionStrategy,
    correction_strategy: CorrectionStrategy,
    emit_callback: Optional[Callable] = None,
    language: str = "zh",
    enable_streaming_correction: bool = True,
    recognition_mode: str = "realtime",
    vad_threshold: int = 500,
    silence_timeout_ms: int = 1000,
    max_segment_ms: int = 15000,
    preview_interval_sec: float = 1.5,
):
    self.transcription = transcription_strategy
    self.correction = correction_strategy
    self.emit = emit_callback or (lambda _event, _payload: None)
    self.language = language
    self.enable_streaming_correction = enable_streaming_correction
    self.recognition_mode = recognition_mode

    self.vad = EnergyVAD(
        threshold=vad_threshold,
        silence_timeout_ms=silence_timeout_ms,
        max_segment_ms=max_segment_ms,
    )
    self.last_text = ""
    self.segment_index = 0
    self.full_text = ""
    self._lock = asyncio.Lock()
    self._last_preview_time = 0.0
    self._preview_interval = preview_interval_sec
    self._raw_segments: list[bytes] = []
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py -v`

Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/session/voice_session.py coloop-agent-voice/tests/test_voice_session.py
git commit -m "refactor(voice): add recognition_mode param to VoiceSession"
```

---

### Task 3: VoiceSession — `realtime_final` Mode

**Files:**
- Modify: `coloop-agent-voice/session/voice_session.py`
- Modify: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write test for realtime_final mode**

Add to `tests/test_voice_session.py`:

```python
@pytest.mark.asyncio
async def test_realtime_final_stores_raw_segments():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection(result="hello")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime_final",
        enable_streaming_correction=False,
    )

    # Simulate two segments
    await session._finalize_segment_audio(b"\x00\x00" * 16000)
    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    assert len(session._raw_segments) == 2
    assert session._raw_segments[0] == b"\x00\x00" * 16000
    assert session._raw_segments[1] == b"\x00\x00" * 16000


@pytest.mark.asyncio
async def test_realtime_final_stop_retranscribes():
    call_count = 0

    class CountingTranscription(TranscriptionStrategy):
        def transcribe(self, audio_bytes, language="zh"):
            nonlocal call_count
            call_count += 1
            return f"text_{call_count}"

        def get_name(self):
            return "counting"

    correction = MockCorrection(result="corrected")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=CountingTranscription(),
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime_final",
        enable_streaming_correction=False,
    )

    # Simulate segments during recording (preview transcriptions)
    await session._finalize_segment_audio(b"\x00\x00" * 16000)
    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    segment_final_events = [e for e in emitted if e[0] == "segment_final"]
    assert len(segment_final_events) == 2

    # Stop should re-transcribe all raw segments
    emitted.clear()
    await session.stop()

    complete_events = [e for e in emitted if e[0] == "complete"]
    assert len(complete_events) == 1
    # The final_text should be the re-transcribed result
    assert "final_text" in complete_events[0][1]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py::test_realtime_final_stores_raw_segments tests/test_voice_session.py::test_realtime_final_stop_retranscribes -v`

Expected: FAIL — `_raw_segments` not populated, `final_text` not in complete event.

- [ ] **Step 3: Implement realtime_final mode in VoiceSession**

In `session/voice_session.py`, modify `_finalize_segment_audio` to store raw segments:

```python
async def _finalize_segment_audio(self, audio_bytes: bytes):
    try:
        # Store raw audio for modes that re-transcribe on stop
        if self.recognition_mode in ("realtime_final", "final_only"):
            self._raw_segments.append(audio_bytes)

        # final_only: skip per-segment transcription entirely
        if self.recognition_mode == "final_only":
            self.segment_index += 1
            self.last_text = ""
            self._last_preview_time = 0.0
            return

        text = self.transcription.transcribe(audio_bytes, language=self.language)
        print(f"[_finalize] {len(audio_bytes)} bytes, result='{text}'")
        if not text:
            self.last_text = ""
            return

        await self.emit(
            "segment_final",
            {"text": text, "segment_index": self.segment_index},
        )

        corrected_text = text
        try:
            corrected_text = await self.correction.correct(text)
            if corrected_text != text:
                await self.emit(
                    "post_corrected",
                    {
                        "text": corrected_text,
                        "original": text,
                        "segment_index": self.segment_index,
                    },
                )
        except Exception:
            corrected_text = text

        self.full_text += corrected_text + " "
        self.segment_index += 1
        self.last_text = ""
        self._last_preview_time = 0.0

    except Exception as e:
        print(f"[_finalize] error: {e}")
        await self.emit("error", {"message": str(e)})
```

Modify `stop` to handle re-transcription:

```python
async def stop(self):
    if self.recognition_mode == "realtime_final":
        # Re-transcribe all raw segments for maximum accuracy
        if self._raw_segments:
            combined = b"".join(self._raw_segments)
            final_text = self._transcribe_long_audio(combined)
            if final_text:
                # Apply correction to final text
                try:
                    corrected = await self.correction.correct(final_text)
                except Exception:
                    corrected = final_text
                await self.emit(
                    "complete",
                    {"full_text": corrected, "final_text": corrected},
                )
            else:
                await self.emit("complete", {"full_text": self.full_text.strip()})
        else:
            await self.emit("complete", {"full_text": self.full_text.strip()})
    elif self.recognition_mode == "final_only":
        # Transcribe all accumulated audio
        all_audio = b"".join(self._raw_segments)
        if all_audio:
            final_text = self._transcribe_long_audio(all_audio)
            if final_text:
                try:
                    corrected = await self.correction.correct(final_text)
                except Exception:
                    corrected = final_text
                await self.emit("complete", {"full_text": corrected})
            else:
                await self.emit("complete", {"full_text": ""})
        else:
            await self.emit("complete", {"full_text": ""})
    else:
        # realtime mode: current behavior
        await self.finalize_segment()
        await self.emit("complete", {"full_text": self.full_text.strip()})
```

Add the `_transcribe_long_audio` stub (will be implemented in Task 4):

```python
def _transcribe_long_audio(self, audio_bytes: bytes) -> str:
    """Transcribe audio, chunking if > 30s."""
    sample_rate = 16000
    bytes_per_sample = 2
    max_chunk_bytes = 30 * sample_rate * bytes_per_sample  # 30s

    if len(audio_bytes) <= max_chunk_bytes:
        return self.transcription.transcribe(audio_bytes, language=self.language)

    # Split at silence boundaries near 25s mark
    chunks = self._split_at_silence(audio_bytes, target_seconds=25)
    texts = []
    for chunk in chunks:
        text = self.transcription.transcribe(chunk, language=self.language)
        if text:
            texts.append(text)
    return " ".join(texts)


def _split_at_silence(self, audio_bytes: bytes, target_seconds: int = 25) -> list[bytes]:
    """Split audio into chunks at silence boundaries near target_seconds."""
    sample_rate = 16000
    bytes_per_sample = 2
    frame_bytes = int(sample_rate * 0.03) * bytes_per_sample  # 30ms frames
    target_bytes = target_seconds * sample_rate * bytes_per_sample

    if len(audio_bytes) <= target_bytes:
        return [audio_bytes]

    chunks = []
    offset = 0
    while offset < len(audio_bytes):
        remaining = len(audio_bytes) - offset
        if remaining <= target_bytes:
            chunks.append(audio_bytes[offset:])
            break

        # Search for silence near target point
        search_start = offset + target_bytes - frame_bytes * 10  # ~300ms before target
        search_end = min(offset + target_bytes + frame_bytes * 10, len(audio_bytes))

        best_split = offset + target_bytes  # fallback
        min_rms = float("inf")

        pos = search_start
        while pos + frame_bytes <= search_end:
            frame = audio_bytes[pos : pos + frame_bytes]
            rms = EnergyVAD._rms(frame)
            if rms < min_rms:
                min_rms = rms
                best_split = pos + frame_bytes
            pos += frame_bytes

        chunks.append(audio_bytes[offset:best_split])
        offset = best_split

    return chunks
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py -v`

Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/session/voice_session.py coloop-agent-voice/tests/test_voice_session.py
git commit -m "feat(voice): implement realtime_final recognition mode with re-transcription"
```

---

### Task 4: VoiceSession — `final_only` Mode

**Files:**
- Modify: `coloop-agent-voice/session/voice_session.py`
- Modify: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write test for final_only mode**

Add to `tests/test_voice_session.py`:

```python
@pytest.mark.asyncio
async def test_final_only_no_segment_events_during_recording():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection(result="hello")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
        enable_streaming_correction=False,
    )

    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    event_types = [e[0] for e in emitted]
    assert "segment_final" not in event_types
    assert "post_corrected" not in event_types


@pytest.mark.asyncio
async def test_final_only_stop_transcribes_all():
    call_count = 0

    class CountingTranscription(TranscriptionStrategy):
        def transcribe(self, audio_bytes, language="zh"):
            nonlocal call_count
            call_count += 1
            return f"result_{call_count}"

        def get_name(self):
            return "counting"

    correction = NoOpCorrectionStrategy()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=CountingTranscription(),
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
    )

    # Feed some audio (simulates VAD segments)
    session._raw_segments.append(b"\x00\x00" * 16000)
    session._raw_segments.append(b"\x00\x00" * 16000)

    await session.stop()

    complete_events = [e for e in emitted if e[0] == "complete"]
    assert len(complete_events) == 1
    full_text = complete_events[0][1]["full_text"]
    assert full_text  # should have transcribed content
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py::test_final_only_no_segment_events_during_recording tests/test_voice_session.py::test_final_only_stop_transcribes_all -v`

Expected: FAIL — `segment_final` events still emitted for final_only mode.

- [ ] **Step 3: Verify implementation from Task 3 covers final_only**

The `_finalize_segment_audio` and `stop` changes in Task 3 already handle `final_only` mode:
- `_finalize_segment_audio`: skips `segment_final`/`post_corrected` events when `recognition_mode == "final_only"`
- `stop`: uses `_transcribe_long_audio` on all `_raw_segments`

Run tests to verify:

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py -v`

Expected: All PASS.

- [ ] **Step 4: Commit (if any fixups needed)**

```bash
git add coloop-agent-voice/session/voice_session.py coloop-agent-voice/tests/test_voice_session.py
git commit -m "feat(voice): implement final_only recognition mode"
```

---

### Task 5: VoiceSession — `_transcribe_long_audio` Chunking

**Files:**
- Modify: `coloop-agent-voice/session/voice_session.py`
- Modify: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write test for chunking logic**

Add to `tests/test_voice_session.py`:

```python
def test_split_at_silence_short_audio():
    """Audio shorter than target should not be split."""
    from session.voice_session import VoiceSession

    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
    )
    # 1 second of audio (32000 bytes)
    audio = b"\x00\x00" * 16000
    chunks = session._split_at_silence(audio, target_seconds=25)
    assert len(chunks) == 1
    assert chunks[0] == audio


def test_split_at_silence_long_audio():
    """Audio longer than target should be split into chunks."""
    from session.voice_session import VoiceSession

    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
    )
    # 60 seconds of audio (1,920,000 bytes)
    audio = b"\x00\x00" * (16000 * 60)
    chunks = session._split_at_silence(audio, target_seconds=25)
    assert len(chunks) >= 2
    # All chunks should be non-empty
    for chunk in chunks:
        assert len(chunk) > 0
    # Reassembled audio should equal original
    reassembled = b"".join(chunks)
    assert reassembled == audio


def test_transcribe_long_audio_short():
    """Short audio should be transcribed directly without chunking."""
    from session.voice_session import VoiceSession

    transcription = MockTranscription(result="short text")
    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=MockCorrection(),
    )
    # 1 second of audio
    audio = b"\x00\x00" * 16000
    result = session._transcribe_long_audio(audio)
    assert result == "short text"
    assert len(transcription.calls) == 1


def test_transcribe_long_audio_long():
    """Long audio should be chunked and each chunk transcribed."""
    from session.voice_session import VoiceSession

    call_count = 0

    class CountingTranscription(TranscriptionStrategy):
        def transcribe(self, audio_bytes, language="zh"):
            nonlocal call_count
            call_count += 1
            return f"chunk_{call_count}"

        def get_name(self):
            return "counting"

    transcription = CountingTranscription()
    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=MockCorrection(),
    )
    # 60 seconds of audio
    audio = b"\x00\x00" * (16000 * 60)
    result = session._transcribe_long_audio(audio)
    assert call_count >= 2
    assert "chunk_1" in result
    assert "chunk_2" in result
```

- [ ] **Step 2: Run tests to verify they pass**

The `_transcribe_long_audio` and `_split_at_silence` methods were already implemented in Task 3. Run:

Run: `cd coloop-agent-voice && python -m pytest tests/test_voice_session.py::test_split_at_silence_short_audio tests/test_voice_session.py::test_split_at_silence_long_audio tests/test_voice_session.py::test_transcribe_long_audio_short tests/test_voice_session.py::test_transcribe_long_audio_long -v`

Expected: All PASS. If any fail, fix the implementation.

- [ ] **Step 3: Commit (if fixups needed)**

```bash
git add coloop-agent-voice/session/voice_session.py coloop-agent-voice/tests/test_voice_session.py
git commit -m "test(voice): add tests for long audio transcription chunking"
```

---

### Task 6: Update `main.py` — Wire New Config to VoiceSession

**Files:**
- Modify: `coloop-agent-voice/main.py`

- [ ] **Step 1: Update `on_start` handler**

In `main.py`, update the `on_start` function to pass new config:

```python
@sio.on("start")
async def on_start(sid, data):
    transcription = await get_transcription_strategy()

    session_config = data.get("config", {})
    enable_post_correction = session_config.get(
        "enable_post_correction", factory.config.get("enablePostCorrection")
    )

    # If post correction disabled by frontend, force no-op
    if enable_post_correction:
        correction = factory.create_correction()
    else:
        from correction.no_op_corrector import NoOpCorrectionStrategy
        correction = NoOpCorrectionStrategy()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        language=factory.config.get("language"),
        enable_streaming_correction=session_config.get(
            "enable_streaming_correction",
            factory.config.get("enableStreamingCorrection"),
        ),
        recognition_mode=session_config.get(
            "recognition_mode",
            factory.config.get("recognitionMode", "realtime"),
        ),
        vad_threshold=session_config.get("vad_threshold", 500),
        silence_timeout_ms=session_config.get("silence_timeout_ms", 1000),
        max_segment_ms=session_config.get("max_segment_ms", 15000),
        preview_interval_sec=session_config.get("preview_interval_sec", 1.5),
    )
    sessions[sid] = session
```

- [ ] **Step 2: Verify no import errors**

Run: `cd coloop-agent-voice && python -c "import main"`

Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-voice/main.py
git commit -m "feat(voice): wire recognition_mode and enable_post_correction to VoiceSession"
```

---

### Task 7: Frontend — HTML Settings Panel

**Files:**
- Modify: `coloop-agent-voice/static/voice-input.html`

- [ ] **Step 1: Replace the settings panel HTML**

Replace the `<div class="settings-panel" id="settingsPanel">` block (lines 250-259) with:

```html
<div class="settings-panel" id="settingsPanel">
    <div class="setting-section-label">识别模式</div>
    <div class="setting-row">
        <span>实时识别</span>
        <div class="radio-btn active" data-group="recognition" data-value="realtime" id="modeRealtime"></div>
    </div>
    <div class="setting-row">
        <span>实时 + 最终识别</span>
        <div class="radio-btn" data-group="recognition" data-value="realtime_final" id="modeRealtimeFinal"></div>
    </div>
    <div class="setting-row">
        <span>仅最终识别</span>
        <div class="radio-btn" data-group="recognition" data-value="final_only" id="modeFinalOnly"></div>
    </div>

    <div class="setting-section-label">纠错</div>
    <div class="setting-row">
        <span>实时修正</span>
        <div class="toggle active" id="streamingToggle"></div>
    </div>
    <div class="setting-row">
        <span>后处理纠错</span>
        <div class="toggle active" id="postToggle"></div>
    </div>

    <div class="setting-section-label">结束操作</div>
    <div class="setting-row">
        <span>无操作</span>
        <div class="radio-btn active" data-group="endaction" data-value="none" id="endNone"></div>
    </div>
    <div class="setting-row">
        <span>自动复制到剪贴板</span>
        <div class="radio-btn" data-group="endaction" data-value="auto_copy" id="endAutoCopy"></div>
    </div>
    <div class="setting-row">
        <span>发送到 coloop</span>
        <div style="display:flex;align-items:center;gap:8px;">
            <span class="ws-status" id="wsStatus">○ 未连接</span>
            <div class="radio-btn" data-group="endaction" data-value="send_coloop" id="endSendColoop"></div>
        </div>
    </div>
</div>
```

- [ ] **Step 2: Add CSS for new elements**

Add these styles inside the `<style>` block, after the `.toggle.active::after` rule:

```css
.setting-section-label {
    color: rgba(255, 255, 255, 0.4);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.setting-section-label:first-child {
    border-top: none;
    padding-top: 0;
}

.radio-btn {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid rgba(255, 255, 255, 0.3);
    position: relative;
    cursor: pointer;
    transition: all 0.25s ease;
    flex-shrink: 0;
}

.radio-btn.active {
    border-color: #34c759;
}

.radio-btn.active::after {
    content: '';
    position: absolute;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #34c759;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
}

.radio-btn.disabled {
    opacity: 0.3;
    cursor: not-allowed;
}

.ws-status {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.4);
}

.ws-status.connected {
    color: #34c759;
}
```

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-voice/static/voice-input.html
git commit -m "feat(voice): add recognition mode, correction, and end action settings UI"
```

---

### Task 8: Frontend — Settings Logic and ColoopConnection

**Files:**
- Modify: `coloop-agent-voice/static/voice-input.js`

- [ ] **Step 1: Add radio button and ColoopConnection logic**

Replace the `setupToggle` function and its calls at the bottom of `voice-input.js` (lines 241-246) with the full settings logic:

```javascript
// --- Toggle and Radio setup ---
function setupToggle(el) {
    el.addEventListener("click", () => {
        if (!el.classList.contains("disabled")) {
            el.classList.toggle("active");
        }
    });
}

function setupRadioGroup(groupName) {
    const radios = document.querySelectorAll(`.radio-btn[data-group="${groupName}"]`);
    radios.forEach((radio) => {
        radio.addEventListener("click", () => {
            if (radio.classList.contains("disabled")) return;
            radios.forEach((r) => r.classList.remove("active"));
            radio.classList.add("active");
        });
    });
}

setupToggle(streamingToggle);
setupToggle(postToggle);
setupRadioGroup("recognition");
setupRadioGroup("endaction");

// --- Recognition mode helpers ---
function getRecognitionMode() {
    const active = document.querySelector('.radio-btn[data-group="recognition"].active');
    return active ? active.dataset.value : "realtime";
}

function getEndAction() {
    const active = document.querySelector('.radio-btn[data-group="endaction"].active');
    return active ? active.dataset.value : "none";
}

// Update UI based on recognition mode
function updateModeUI() {
    const mode = getRecognitionMode();
    if (mode === "final_only") {
        realtimeText.style.display = "none";
    } else {
        realtimeText.style.display = "";
    }
}

// Watch for mode changes
document.querySelectorAll('.radio-btn[data-group="recognition"]').forEach((btn) => {
    btn.addEventListener("click", () => setTimeout(updateModeUI, 0));
});

// --- ColoopConnection ---
class ColoopConnection {
    constructor() {
        this.ws = null;
        this.wsUrl = null;
        this.connected = false;
    }

    connect(wsUrl) {
        this.wsUrl = wsUrl;
        this._doConnect();
    }

    _doConnect() {
        if (this.ws) {
            this.ws.close();
        }
        try {
            this.ws = new WebSocket(this.wsUrl);
        } catch (e) {
            console.warn("[coloop] invalid WS URL:", this.wsUrl);
            this._updateStatus(false);
            return;
        }

        this.ws.onopen = () => {
            console.log("[coloop] connected to", this.wsUrl);
            this.connected = true;
            this._updateStatus(true);
        };

        this.ws.onclose = () => {
            console.log("[coloop] disconnected");
            this.connected = false;
            this._updateStatus(false);
            // Auto-reconnect after 3s
            setTimeout(() => this._doConnect(), 3000);
        };

        this.ws.onerror = (e) => {
            console.warn("[coloop] WS error", e);
        };

        this.ws.onmessage = (e) => {
            // coloop sends JSON messages; we don't need to handle incoming for now
            console.log("[coloop] message:", e.data);
        };
    }

    send(text) {
        if (this.ws && this.connected) {
            this.ws.send(JSON.stringify({ action: "chat", message: text }));
            return true;
        }
        return false;
    }

    _updateStatus(connected) {
        const statusEl = document.getElementById("wsStatus");
        const radioEl = document.getElementById("endSendColoop");
        if (connected) {
            statusEl.textContent = "● 已连接";
            statusEl.classList.add("connected");
            radioEl.classList.remove("disabled");
        } else {
            statusEl.textContent = "○ 未连接";
            statusEl.classList.remove("connected");
            radioEl.classList.add("disabled");
            // If send_coloop was selected, switch to none
            if (getEndAction() === "send_coloop") {
                document.querySelector('.radio-btn[data-group="endaction"][data-value="none"]').classList.add("active");
                document.getElementById("endSendColoop").classList.remove("active");
            }
        }
    }
}

const coloopConn = new ColoopConnection();

// Connect to coloop on page load (URL will be fetched from server config)
fetch("/api/config")
    .then((r) => r.json())
    .then((cfg) => {
        if (cfg.coloopWsUrl) {
            coloopConn.connect(cfg.coloopWsUrl);
        }
    })
    .catch(() => console.log("[coloop] no config endpoint, WS disabled"));
```

- [ ] **Step 2: Update `startRecording` to pass new config**

In `startRecording()`, update the `socket.emit("start", ...)` block (around line 103):

```javascript
socket.emit("start", {
    config: {
        lang: "zh",
        enable_streaming_correction: streamingToggle.classList.contains("active"),
        enable_post_correction: postToggle.classList.contains("active"),
        recognition_mode: getRecognitionMode(),
    }
});
```

- [ ] **Step 3: Update `complete` event handler for end actions**

Replace the `socket.on("complete", ...)` handler (around line 138):

```javascript
socket.on("complete", (data) => {
    console.log("[socket] complete:", data);
    statusText.textContent = "录音完成";

    // Use final_text if available (realtime_final mode)
    const text = data.final_text || data.full_text;
    if (data.final_text) {
        resultArea.textContent = data.final_text;
    }

    // Handle end action
    const endAction = getEndAction();
    if (endAction === "auto_copy" && text) {
        navigator.clipboard.writeText(text).then(() => {
            statusText.textContent = "已复制到剪贴板";
        }).catch(() => {
            statusText.textContent = "复制失败";
        });
    } else if (endAction === "send_coloop" && text) {
        if (coloopConn.send(text)) {
            statusText.textContent = "已发送到 coloop";
        } else {
            statusText.textContent = "发送失败：coloop 未连接";
        }
    }
});
```

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-voice/static/voice-input.js
git commit -m "feat(voice): add settings logic, ColoopConnection, and end action handling"
```

---

### Task 9: Backend — Add Config API Endpoint

**Files:**
- Modify: `coloop-agent-voice/main.py`

- [ ] **Step 1: Add `/api/config` endpoint**

In `main.py`, add before the Socket.IO event handlers:

```python
@app.get("/api/config")
async def get_config():
    return {
        "language": factory.config.get("language"),
        "recognitionMode": factory.config.get("recognitionMode", "realtime"),
        "enableStreamingCorrection": factory.config.get("enableStreamingCorrection"),
        "enablePostCorrection": factory.config.get("enablePostCorrection"),
        "coloopWsUrl": factory.config.get_coloop_ws_url(),
    }
```

- [ ] **Step 2: Verify endpoint works**

Run: `cd coloop-agent-voice && python -c "from main import app; print('OK')"`

Expected: OK.

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-voice/main.py
git commit -m "feat(voice): add /api/config endpoint for frontend settings initialization"
```

---

### Task 10: Final Integration Test and Cleanup

**Files:**
- Modify: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write integration test for all three modes end-to-end**

Add to `tests/test_voice_session.py`:

```python
@pytest.mark.asyncio
async def test_realtime_mode_full_flow():
    """Realtime mode: segments produce events, complete has full_text."""
    transcription = MockTranscription(result="word")
    correction = NoOpCorrectionStrategy()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime",
        enable_streaming_correction=False,
    )

    await session._finalize_segment_audio(b"\x00\x00" * 16000)
    await session.stop()

    types = [e[0] for e in emitted]
    assert "segment_final" in types
    assert "complete" in types
    complete = [e for e in emitted if e[0] == "complete"][0]
    assert "word" in complete[1]["full_text"]


@pytest.mark.asyncio
async def test_realtime_final_mode_full_flow():
    """RealtimeFinal mode: segments fire, stop produces final_text."""
    call_count = 0

    class CountingTranscription(TranscriptionStrategy):
        def transcribe(self, audio_bytes, language="zh"):
            nonlocal call_count
            call_count += 1
            return f"final_{call_count}"

        def get_name(self):
            return "counting"

    correction = NoOpCorrectionStrategy()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=CountingTranscription(),
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime_final",
        enable_streaming_correction=False,
    )

    await session._finalize_segment_audio(b"\x00\x00" * 16000)
    emitted.clear()
    await session.stop()

    complete = [e for e in emitted if e[0] == "complete"][0]
    assert "final_text" in complete[1]


@pytest.mark.asyncio
async def test_final_only_mode_full_flow():
    """FinalOnly mode: no segment events, stop transcribes all."""
    transcription = MockTranscription(result="all done")
    correction = NoOpCorrectionStrategy()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
    )

    session._raw_segments.append(b"\x00\x00" * 16000)
    session._raw_segments.append(b"\x00\x00" * 16000)
    await session.stop()

    types = [e[0] for e in emitted]
    assert "segment_final" not in types
    complete = [e for e in emitted if e[0] == "complete"][0]
    assert complete[1]["full_text"] == "all done"
```

- [ ] **Step 2: Run all tests**

Run: `cd coloop-agent-voice && python -m pytest tests/ -v`

Expected: All PASS.

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-voice/tests/test_voice_session.py
git commit -m "test(voice): add integration tests for all recognition modes"
```
