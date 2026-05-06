# Voice Input Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `coloop-agent-voice`, an independent Python-based voice-to-text service using faster-whisper with configurable streaming and post-processing correction.

**Architecture:** FastAPI + python-socketio for WebSocket audio streaming. Audio flows through VAD detection → Whisper transcription → streaming diff correction → optional LLM post-correction. Frontend is a single-page Apple-style liquid-glass UI.

**Tech Stack:** Python 3.10+, FastAPI, python-socketio, faster-whisper, webrtcvad, numpy, httpx, pytest

---

## File Structure

```
coloop-agent-voice/
├── main.py                         ← FastAPI app + Socket.IO event handlers
├── config.py                       ← Configuration loading (env + JSON)
├── requirements.txt
├── .env.example
├── README.md
├── engine/
│   ├── __init__.py
│   ├── whisper_engine.py           ← faster-whisper wrapper
│   └── http_adapter.py             ← External HTTP engine stub
├── audio/
│   ├── __init__.py
│   └── vad_processor.py            ← WebRTC VAD + voice segmentation
├── correction/
│   ├── __init__.py
│   ├── streaming_diff.py           ← Text diff for incremental updates
│   └── post_corrector.py           ← LLM API post-processing
├── session/
│   ├── __init__.py
│   └── voice_session.py            ← Per-connection session state machine
├── static/
│   ├── voice-input.html            ← Apple liquid-glass UI
│   ├── voice-input.js              ← Audio capture + Socket.IO client
│   └── audio-processor.js          ← AudioWorklet PCM processor
└── tests/
    ├── test_config.py
    ├── test_vad_processor.py
    ├── test_streaming_diff.py
    ├── test_whisper_engine.py
    ├── test_post_corrector.py
    ├── test_voice_session.py
    └── test_websocket_handler.py
```

---

## Task 1: Project Skeleton

**Files:**
- Create: `coloop-agent-voice/requirements.txt`
- Create: `coloop-agent-voice/.env.example`
- Create: `coloop-agent-voice/config.py`
- Create: `coloop-agent-voice/tests/test_config.py`

- [ ] **Step 1: Create directory structure**

Run:
```bash
mkdir -p coloop-agent-voice/{engine,audio,correction,session,static,tests,models}
touch coloop-agent-voice/{engine,audio,correction,session}/__init__.py
```

- [ ] **Step 2: Write requirements.txt**

Create `coloop-agent-voice/requirements.txt`:
```
fastapi>=0.110.0
python-socketio>=5.11.0
uvicorn[standard]>=0.29.0
faster-whisper>=1.0.0
webrtcvad>=2.0.10
numpy>=1.26.0
httpx>=0.27.0
python-dotenv>=1.0.0
pytest>=8.0.0
pytest-asyncio>=0.23.0
```

- [ ] **Step 3: Write .env.example**

Create `coloop-agent-voice/.env.example`:
```bash
HOST=0.0.0.0
PORT=8000
WHISPER_MODEL=base
WHISPER_DEVICE=cpu
WHISPER_COMPUTE_TYPE=int8
WHISPER_MODEL_DIR=./models
DEFAULT_LANG=zh
ENABLE_STREAMING_CORRECTION=true
ENABLE_POST_CORRECTION=false
SETTING_FILE=
POST_CORRECTION_MODEL=minimax
```

- [ ] **Step 4: Write config.py**

Create `coloop-agent-voice/config.py`:
```python
import os
import json
from pathlib import Path
from dotenv import load_dotenv
from typing import Optional, Dict, Any

load_dotenv()


class VoiceConfig:
    def __init__(self):
        self.host = os.getenv("HOST", "0.0.0.0")
        self.port = int(os.getenv("PORT", "8000"))
        self.whisper_model = os.getenv("WHISPER_MODEL", "base")
        self.whisper_device = os.getenv("WHISPER_DEVICE", "cpu")
        self.whisper_compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.whisper_model_dir = Path(os.getenv("WHISPER_MODEL_DIR", "./models"))
        self.default_lang = os.getenv("DEFAULT_LANG", "zh")
        self.enable_streaming_correction = os.getenv("ENABLE_STREAMING_CORRECTION", "true").lower() == "true"
        self.enable_post_correction = os.getenv("ENABLE_POST_CORRECTION", "false").lower() == "true"
        self.setting_file = os.getenv("SETTING_FILE", "")
        self.post_correction_model = os.getenv("POST_CORRECTION_MODEL", "")

        self._models: Dict[str, Any] = {}
        self._load_models()

    def _load_models(self):
        if self.setting_file and Path(self.setting_file).exists():
            with open(self.setting_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                self._models = data.get("models", {})

    def get_model_config(self, name: str) -> Optional[Dict[str, str]]:
        return self._models.get(name)

    def get_post_correction_config(self) -> Optional[Dict[str, str]]:
        if not self.enable_post_correction or not self.post_correction_model:
            return None
        cfg = self._models.get(self.post_correction_model)
        if cfg:
            return {
                "api_base": cfg.get("apiBase", ""),
                "api_key": cfg.get("apiKey", ""),
                "model": cfg.get("model", ""),
            }
        return None
```

- [ ] **Step 5: Write test_config.py**

Create `coloop-agent-voice/tests/test_config.py`:
```python
import os
import json
import tempfile
from pathlib import Path

import pytest

from config import VoiceConfig


class TestVoiceConfig:
    def test_default_values(self):
        cfg = VoiceConfig()
        assert cfg.host == "0.0.0.0"
        assert cfg.port == 8000
        assert cfg.whisper_model == "base"
        assert cfg.whisper_device == "cpu"
        assert cfg.enable_streaming_correction is True
        assert cfg.enable_post_correction is False

    def test_load_models_from_setting_file(self):
        setting = {"models": {"test-model": {"apiBase": "https://test.com", "apiKey": "secret", "model": "gpt-4"}}}
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(setting, f)
            path = f.name

        os.environ["SETTING_FILE"] = path
        os.environ["POST_CORRECTION_MODEL"] = "test-model"
        os.environ["ENABLE_POST_CORRECTION"] = "true"
        try:
            cfg = VoiceConfig()
            model_cfg = cfg.get_post_correction_config()
            assert model_cfg is not None
            assert model_cfg["api_base"] == "https://test.com"
            assert model_cfg["api_key"] == "secret"
            assert model_cfg["model"] == "gpt-4"
        finally:
            del os.environ["SETTING_FILE"]
            del os.environ["POST_CORRECTION_MODEL"]
            del os.environ["ENABLE_POST_CORRECTION"]
            Path(path).unlink()
```

- [ ] **Step 6: Run tests**

Run:
```bash
cd coloop-agent-voice
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install pytest python-dotenv
pytest tests/test_config.py -v
```
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add coloop-agent-voice/
git commit -m "feat(voice): project skeleton, config, and tests"
```

---

## Task 2: VAD Audio Processor

**Files:**
- Create: `coloop-agent-voice/audio/vad_processor.py`
- Create: `coloop-agent-voice/tests/test_vad_processor.py`

- [ ] **Step 1: Write test_vad_processor.py**

Create `coloop-agent-voice/tests/test_vad_processor.py`:
```python
import pytest
from unittest.mock import Mock, patch

from audio.vad_processor import VADProcessor


class TestVADProcessor:
    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_voice_segment_detected(self, mock_vad_class):
        mock_vad = Mock()
        # Simulate: 10 frames of silence, then 10 frames of speech, then 10 frames of silence
        mock_vad.is_speech.side_effect = (
            [False] * 10 + [True] * 10 + [False] * 10
        )
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor(aggressiveness=3, sample_rate=16000, frame_duration_ms=30)

        # Feed silence (should not trigger)
        silence = b"\x00" * (16000 * 2 * 30 // 1000)  # 30ms of 16bit silence
        for _ in range(10):
            result = processor.process(silence)
            assert result is None

        # Feed speech (should trigger and start accumulating)
        speech = b"\x01\x00" * (16000 * 30 // 1000)
        for _ in range(10):
            result = processor.process(speech)

        # Feed silence again (should trigger end of segment)
        for i in range(10):
            result = processor.process(silence)
            if i >= 9:  # After enough unvoiced frames
                assert result is not None
                assert isinstance(result, bytes)

    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_flush_returns_remaining(self, mock_vad_class):
        mock_vad = Mock()
        mock_vad.is_speech.return_value = True
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor()
        speech = b"\x01\x00" * (16000 * 30 // 1000)
        processor.process(speech)

        result = processor.flush()
        assert result is not None
        assert isinstance(result, bytes)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_vad_processor.py -v
```
Expected: FAIL with "ModuleNotFoundError: No module named 'audio.vad_processor'" or import errors.

- [ ] **Step 3: Write vad_processor.py**

Create `coloop-agent-voice/audio/vad_processor.py`:
```python
import collections
import webrtcvad


class VADProcessor:
    def __init__(self, aggressiveness: int = 3, sample_rate: int = 16000, frame_duration_ms: int = 30):
        self.vad = webrtcvad.Vad(aggressiveness)
        self.sample_rate = sample_rate
        self.frame_duration_ms = frame_duration_ms
        self.frame_bytes = int(sample_rate * frame_duration_ms / 1000) * 2
        self.ring_buffer = collections.deque(maxlen=50)
        self.triggered = False
        self.buffer = bytearray()

    def process(self, pcm_bytes: bytes) -> bytes | None:
        frames = []
        offset = 0
        while offset + self.frame_bytes <= len(pcm_bytes):
            frame = pcm_bytes[offset : offset + self.frame_bytes]
            frames.append((frame, self.vad.is_speech(frame, self.sample_rate)))
            offset += self.frame_bytes

        for frame, is_speech in frames:
            if not self.triggered:
                self.ring_buffer.append((frame, is_speech))
                num_voiced = sum(1 for _, s in self.ring_buffer if s)
                if num_voiced > 0.9 * len(self.ring_buffer):
                    self.triggered = True
                    self.buffer.extend(b"".join(f for f, _ in self.ring_buffer))
                    self.ring_buffer.clear()
            else:
                self.buffer.extend(frame)
                self.ring_buffer.append((frame, is_speech))
                num_unvoiced = sum(1 for _, s in self.ring_buffer if not s)
                if num_unvoiced > 0.9 * len(self.ring_buffer):
                    self.triggered = False
                    result = bytes(self.buffer)
                    self.buffer = bytearray()
                    self.ring_buffer.clear()
                    return result

        return None

    def flush(self) -> bytes | None:
        if self.triggered and len(self.buffer) > 0:
            result = bytes(self.buffer)
            self.buffer = bytearray()
            self.triggered = False
            self.ring_buffer.clear()
            return result
        return None
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_vad_processor.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/audio/vad_processor.py coloop-agent-voice/tests/test_vad_processor.py
git commit -m "feat(voice): VAD audio processor with webrtcvad"
```

---

## Task 3: Streaming Text Diff

**Files:**
- Create: `coloop-agent-voice/correction/streaming_diff.py`
- Create: `coloop-agent-voice/tests/test_streaming_diff.py`

- [ ] **Step 1: Write test_streaming_diff.py**

Create `coloop-agent-voice/tests/test_streaming_diff.py`:
```python
from correction.streaming_diff import streaming_diff


class TestStreamingDiff:
    def test_no_change(self):
        result = streaming_diff("hello world", "hello world")
        assert result["changed"] is False
        assert result["current"] == "hello world"

    def test_simple_change(self):
        result = streaming_diff("hello worl", "hello world")
        assert result["changed"] is True
        assert result["current"] == "hello world"
        assert result["previous"] == "hello worl"

    def test_chinese_text(self):
        result = streaming_diff("今天天气", "今天天气很好")
        assert result["changed"] is True
        assert result["current"] == "今天天气很好"
        assert result["previous"] == "今天天气"

    def test_empty_to_text(self):
        result = streaming_diff("", "first result")
        assert result["changed"] is True
        assert result["current"] == "first result"
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_streaming_diff.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write streaming_diff.py**

Create `coloop-agent-voice/correction/streaming_diff.py`:
```python
def streaming_diff(old_text: str, new_text: str) -> dict:
    """Compare two text strings and return difference metadata."""
    if old_text == new_text:
        return {"changed": False, "current": new_text}

    return {
        "changed": True,
        "current": new_text,
        "previous": old_text,
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_streaming_diff.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/correction/streaming_diff.py coloop-agent-voice/tests/test_streaming_diff.py
git commit -m "feat(voice): streaming text diff for incremental updates"
```

---

## Task 4: Whisper Recognition Engine

**Files:**
- Create: `coloop-agent-voice/engine/whisper_engine.py`
- Create: `coloop-agent-voice/tests/test_whisper_engine.py`

- [ ] **Step 1: Write test_whisper_engine.py**

Create `coloop-agent-voice/tests/test_whisper_engine.py`:
```python
import numpy as np
from unittest.mock import Mock, patch, MagicMock

from engine.whisper_engine import WhisperEngine


class TestWhisperEngine:
    @patch("engine.whisper_engine.WhisperModel")
    def test_transcribe(self, mock_model_class):
        mock_segment = Mock()
        mock_segment.text = "  hello world  "
        mock_model = Mock()
        mock_model.transcribe.return_value = ([mock_segment], None)
        mock_model_class.return_value = mock_model

        engine = WhisperEngine("base", device="cpu", compute_type="int8")
        audio_bytes = np.zeros(16000, dtype=np.int16).tobytes()
        result = engine.transcribe(audio_bytes, language="en")

        assert result == "hello world"
        mock_model.transcribe.assert_called_once()
        call_kwargs = mock_model.transcribe.call_args[1]
        assert call_kwargs["language"] == "en"
        assert call_kwargs["beam_size"] == 5
        assert call_kwargs["condition_on_previous_text"] is True

    @patch("engine.whisper_engine.WhisperModel")
    def test_transcribe_multiple_segments(self, mock_model_class):
        seg1 = Mock()
        seg1.text = "first"
        seg2 = Mock()
        seg2.text = "second"
        mock_model = Mock()
        mock_model.transcribe.return_value = ([seg1, seg2], None)
        mock_model_class.return_value = mock_model

        engine = WhisperEngine("base")
        audio_bytes = np.zeros(16000, dtype=np.int16).tobytes()
        result = engine.transcribe(audio_bytes, language="zh")

        assert result == "first second"
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_whisper_engine.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write whisper_engine.py**

Create `coloop-agent-voice/engine/whisper_engine.py`:
```python
import numpy as np
from faster_whisper import WhisperModel


class WhisperEngine:
    def __init__(self, model_path: str, device: str = "cpu", compute_type: str = "int8"):
        self.model = WhisperModel(model_path, device=device, compute_type=compute_type)

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        audio_np = np.frombuffer(audio_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        segments, _ = self.model.transcribe(
            audio_np,
            language=language,
            beam_size=5,
            condition_on_previous_text=True,
        )
        return " ".join([seg.text.strip() for seg in segments]).strip()
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_whisper_engine.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/engine/whisper_engine.py coloop-agent-voice/tests/test_whisper_engine.py
git commit -m "feat(voice): Whisper engine wrapper with faster-whisper"
```

---

## Task 5: Post-Processing Corrector

**Files:**
- Create: `coloop-agent-voice/correction/post_corrector.py`
- Create: `coloop-agent-voice/tests/test_post_corrector.py`

- [ ] **Step 1: Write test_post_corrector.py**

Create `coloop-agent-voice/tests/test_post_corrector.py`:
```python
import pytest
from unittest.mock import AsyncMock, patch

from correction.post_corrector import PostCorrector


class TestPostCorrector:
    @pytest.mark.asyncio
    async def test_correct_success(self):
        corrector = PostCorrector("https://api.test.com", "test-key", "gpt-4")

        mock_response = AsyncMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "choices": [{"message": {"content": "corrected text"}}]
        }
        mock_response.raise_for_status = Mock()

        with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
            mock_post.return_value = mock_response
            result = await corrector.correct("raw text")
            assert result == "corrected text"

            call_args = mock_post.call_args
            assert call_args[1]["headers"]["Authorization"] == "Bearer test-key"
            assert "raw text" in call_args[1]["json"]["messages"][1]["content"]

    @pytest.mark.asyncio
    async def test_correct_api_error(self):
        corrector = PostCorrector("https://api.test.com", "test-key", "gpt-4")

        with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
            mock_post.side_effect = Exception("API error")
            with pytest.raises(Exception, match="API error"):
                await corrector.correct("raw text")
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_post_corrector.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write post_corrector.py**

Create `coloop-agent-voice/correction/post_corrector.py`:
```python
import httpx


class PostCorrector:
    PROMPT = """请对以下语音识别结果进行纠错和润色，修正可能的同音字错误、标点缺失，保持原意不变。只输出修正后的文本，不要解释。

原文：{text}"""

    def __init__(self, api_base: str, api_key: str, model: str):
        self.api_base = api_base.rstrip("/")
        self.api_key = api_key
        self.model = model

    async def correct(self, text: str) -> str:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{self.api_base}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "messages": [
                        {"role": "system", "content": "你是一个语音识别后处理纠错助手。"},
                        {"role": "user", "content": self.PROMPT.format(text=text)},
                    ],
                    "temperature": 0.3,
                    "max_tokens": 1024,
                },
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"].strip()
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_post_corrector.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/correction/post_corrector.py coloop-agent-voice/tests/test_post_corrector.py
git commit -m "feat(voice): LLM post-processing corrector"
```

---

## Task 6: HTTP Engine Adapter (Stub)

**Files:**
- Create: `coloop-agent-voice/engine/http_adapter.py`
- Create: `coloop-agent-voice/tests/test_http_adapter.py`

- [ ] **Step 1: Write test_http_adapter.py**

Create `coloop-agent-voice/tests/test_http_adapter.py`:
```python
import pytest
from engine.http_adapter import HttpAdapter


class TestHttpAdapter:
    def test_init(self):
        adapter = HttpAdapter("https://api.example.com/asr")
        assert adapter.endpoint == "https://api.example.com/asr"

    @pytest.mark.asyncio
    async def test_transcribe_not_implemented(self):
        adapter = HttpAdapter("https://api.example.com/asr")
        with pytest.raises(NotImplementedError):
            await adapter.transcribe(b"audio", language="zh")
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_http_adapter.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write http_adapter.py**

Create `coloop-agent-voice/engine/http_adapter.py`:
```python
class HttpAdapter:
    def __init__(self, endpoint: str):
        self.endpoint = endpoint

    async def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        raise NotImplementedError("HTTP adapter not yet implemented")
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_http_adapter.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/engine/http_adapter.py coloop-agent-voice/tests/test_http_adapter.py
git commit -m "feat(voice): HTTP engine adapter stub for external ASR"
```

---

## Task 7: Voice Session Manager

**Files:**
- Create: `coloop-agent-voice/session/voice_session.py`
- Create: `coloop-agent-voice/tests/test_voice_session.py`

- [ ] **Step 1: Write test_voice_session.py**

Create `coloop-agent-voice/tests/test_voice_session.py`:
```python
import asyncio
import pytest
from unittest.mock import AsyncMock, Mock, patch

from session.voice_session import VoiceSession


class TestVoiceSession:
    @pytest.fixture
    def mock_engine(self):
        engine = Mock()
        engine.transcribe.return_value = "test result"
        return engine

    @pytest.fixture
    def mock_emit(self):
        return AsyncMock()

    @pytest.mark.asyncio
    async def test_feed_audio_triggers_transcribe(self, mock_engine, mock_emit):
        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": True},
            engine=mock_engine,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * (16000 * 30 // 1000)
        with patch.object(session.vad, "process", return_value=audio):
            await session.feed_audio(audio)

        mock_engine.transcribe.assert_called_once()
        mock_emit.assert_called()

    @pytest.mark.asyncio
    async def test_streaming_correction_disabled(self, mock_engine, mock_emit):
        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": False},
            engine=mock_engine,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * (16000 * 30 // 1000)
        with patch.object(session.vad, "process", return_value=audio):
            await session.feed_audio(audio)

        # Should still transcribe but not emit partial updates
        mock_engine.transcribe.assert_called_once()

    @pytest.mark.asyncio
    async def test_finalize_segment(self, mock_engine, mock_emit):
        corrector = AsyncMock()
        corrector.correct.return_value = "corrected result"

        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": True, "enable_post_correction": True},
            engine=mock_engine,
            post_corrector=corrector,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * 100
        with patch.object(session.vad, "flush", return_value=audio):
            await session.finalize_segment()

        mock_emit.assert_any_call("segment_final", {"text": "test result", "segment_index": 0})
        mock_emit.assert_any_call("post_corrected", {"text": "corrected result", "original": "test result", "segment_index": 0})

    @pytest.mark.asyncio
    async def test_stop(self, mock_engine, mock_emit):
        session = VoiceSession(
            config={"lang": "zh"},
            engine=mock_engine,
            emit_callback=mock_emit,
        )

        with patch.object(session, "finalize_segment", new_callable=AsyncMock):
            await session.stop()
            mock_emit.assert_called_with("complete", {"full_text": ""})
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_voice_session.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write voice_session.py**

Create `coloop-agent-voice/session/voice_session.py`:
```python
import asyncio
from typing import Optional, Callable

from engine.whisper_engine import WhisperEngine
from audio.vad_processor import VADProcessor
from correction.streaming_diff import streaming_diff
from correction.post_corrector import PostCorrector


class VoiceSession:
    def __init__(
        self,
        config: dict,
        engine: WhisperEngine,
        post_corrector: Optional[PostCorrector] = None,
        emit_callback: Optional[Callable] = None,
    ):
        self.config = config
        self.engine = engine
        self.post_corrector = post_corrector
        self.emit = emit_callback or (lambda _event, _payload: None)

        self.vad = VADProcessor()
        self.last_text = ""
        self.segment_index = 0
        self.full_text = ""
        self._lock = asyncio.Lock()

    async def feed_audio(self, pcm_bytes: bytes):
        async with self._lock:
            segment = self.vad.process(pcm_bytes)
            if segment:
                await self._transcribe_segment(segment)

    async def _transcribe_segment(self, audio_bytes: bytes):
        try:
            text = self.engine.transcribe(
                audio_bytes, language=self.config.get("lang", "zh")
            )
            if not text:
                return

            if self.config.get("enable_streaming_correction", True):
                diff = streaming_diff(self.last_text, text)
                if diff["changed"]:
                    await self.emit(
                        "partial",
                        {
                            "text": text,
                            "segment_index": self.segment_index,
                            "is_stable": False,
                        },
                    )
                    self.last_text = text
            else:
                self.last_text = text

        except Exception as e:
            await self.emit("error", {"message": str(e)})

    async def finalize_segment(self):
        async with self._lock:
            segment = self.vad.flush()
            if not segment:
                return

            text = self.engine.transcribe(
                segment, language=self.config.get("lang", "zh")
            )
            if not text:
                return

            self.last_text = text
            await self.emit(
                "segment_final",
                {"text": text, "segment_index": self.segment_index},
            )

            corrected_text = text
            if self.post_corrector and self.config.get("enable_post_correction", False):
                try:
                    corrected_text = await self.post_corrector.correct(text)
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

    async def stop(self):
        await self.finalize_segment()
        await self.emit("complete", {"full_text": self.full_text.strip()})
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_voice_session.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/session/voice_session.py coloop-agent-voice/tests/test_voice_session.py
git commit -m "feat(voice): voice session manager with VAD + engine + correction pipeline"
```

---

## Task 8: WebSocket Handler and FastAPI App

**Files:**
- Create: `coloop-agent-voice/main.py`
- Create: `coloop-agent-voice/tests/test_websocket_handler.py`

- [ ] **Step 1: Write test_websocket_handler.py**

Create `coloop-agent-voice/tests/test_websocket_handler.py`:
```python
import pytest
from unittest.mock import Mock, patch, AsyncMock

import main


class TestWebSocketHandler:
    @pytest.fixture(autouse=True)
    def reset_sessions(self):
        main.sessions.clear()
        yield
        main.sessions.clear()

    @pytest.mark.asyncio
    async def test_connect(self):
        with patch("main.sio.emit", new_callable=AsyncMock) as mock_emit:
            await main.on_start("test-sid", {"config": {"lang": "zh"}})
            assert "test-sid" in main.sessions
            assert isinstance(main.sessions["test-sid"], main.VoiceSession)

    @pytest.mark.asyncio
    async def test_audio(self):
        mock_session = AsyncMock()
        main.sessions["test-sid"] = mock_session

        audio_data = b"test audio bytes"
        await main.on_audio("test-sid", audio_data)
        mock_session.feed_audio.assert_called_once_with(audio_data)

    @pytest.mark.asyncio
    async def test_stop(self):
        mock_session = AsyncMock()
        main.sessions["test-sid"] = mock_session

        await main.on_stop("test-sid", {})
        mock_session.stop.assert_called_once()
        assert "test-sid" not in main.sessions
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_websocket_handler.py -v
```
Expected: FAIL with import errors.

- [ ] **Step 3: Write main.py**

Create `coloop-agent-voice/main.py`:
```python
import socketio
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from config import VoiceConfig
from engine.whisper_engine import WhisperEngine
from correction.post_corrector import PostCorrector
from session.voice_session import VoiceSession

config = VoiceConfig()
sio = socketio.AsyncServer(async_mode="asgi", cors_allowed_origins="*")
app = FastAPI()
socket_app = socketio.ASGIApp(sio, app)

# Static files
app.mount("/static", StaticFiles(directory="static"), name="static")

# Global engine instance (lazy-loaded)
_engine = None


def get_engine():
    global _engine
    if _engine is None:
        model_path = str(config.whisper_model_dir / config.whisper_model)
        _engine = WhisperEngine(
            model_path,
            device=config.whisper_device,
            compute_type=config.whisper_compute_type,
        )
    return _engine


def get_post_corrector():
    if not config.enable_post_correction:
        return None
    model_cfg = config.get_post_correction_config()
    if model_cfg:
        return PostCorrector(
            model_cfg["api_base"],
            model_cfg["api_key"],
            model_cfg["model"],
        )
    return None


sessions = {}


@sio.event
async def connect(sid, environ):
    print(f"Client connected: {sid}")


@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")
    if sid in sessions:
        del sessions[sid]


@sio.on("start")
async def on_start(sid, data):
    engine = get_engine()
    corrector = get_post_corrector()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session = VoiceSession(
        config=data.get("config", {}),
        engine=engine,
        post_corrector=corrector,
        emit_callback=emit,
    )
    sessions[sid] = session


@sio.on("audio")
async def on_audio(sid, data):
    if sid in sessions:
        await sessions[sid].feed_audio(data)


@sio.on("stop")
async def on_stop(sid, data):
    if sid in sessions:
        await sessions[sid].stop()
        del sessions[sid]


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(socket_app, host=config.host, port=config.port)
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd coloop-agent-voice
pytest tests/test_websocket_handler.py -v
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add coloop-agent-voice/main.py coloop-agent-voice/tests/test_websocket_handler.py
git commit -m "feat(voice): FastAPI + Socket.IO WebSocket handler and main app"
```

---

## Task 9: Frontend - Apple Liquid Glass UI

**Files:**
- Create: `coloop-agent-voice/static/voice-input.html`
- Create: `coloop-agent-voice/static/voice-input.js`
- Create: `coloop-agent-voice/static/audio-processor.js`

- [ ] **Step 1: Write audio-processor.js**

Create `coloop-agent-voice/static/audio-processor.js`:
```javascript
class PCMProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.bufferSize = 4800; // 300ms @ 16kHz
        this.buffer = new Float32Array(this.bufferSize);
        this.offset = 0;
    }

    process(inputs, outputs, parameters) {
        const input = inputs[0];
        if (!input || !input[0]) return true;

        const samples = input[0];

        for (let i = 0; i < samples.length; i++) {
            this.buffer[this.offset++] = samples[i];

            if (this.offset >= this.bufferSize) {
                const int16Array = new Int16Array(this.bufferSize);
                for (let j = 0; j < this.bufferSize; j++) {
                    const val = this.buffer[j] * 32767;
                    int16Array[j] = val > 32767 ? 32767 : (val < -32768 ? -32768 : val);
                }
                this.port.postMessage(int16Array.buffer, [int16Array.buffer]);
                this.offset = 0;
            }
        }
        return true;
    }
}

registerProcessor("pcm-processor", PCMProcessor);
```

- [ ] **Step 2: Write voice-input.html**

Create `coloop-agent-voice/static/voice-input.html`:
```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>语音输入器</title>
    <script src="https://cdn.socket.io/4.7.5/socket.io.min.js"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", sans-serif;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            overflow: hidden;
            padding: 20px;
        }

        .glass-panel {
            background: rgba(255, 255, 255, 0.08);
            backdrop-filter: blur(40px) saturate(180%);
            -webkit-backdrop-filter: blur(40px) saturate(180%);
            border: 1px solid rgba(255, 255, 255, 0.15);
            border-radius: 28px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.1);
            padding: 36px;
            width: 100%;
            max-width: 560px;
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        .header {
            text-align: center;
            color: rgba(255, 255, 255, 0.9);
            font-size: 22px;
            font-weight: 600;
            letter-spacing: -0.5px;
        }

        .mic-wrapper {
            display: flex;
            justify-content: center;
            padding: 8px 0;
        }

        .mic-btn {
            width: 88px;
            height: 88px;
            border-radius: 50%;
            border: none;
            background: rgba(255, 255, 255, 0.12);
            backdrop-filter: blur(20px);
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 36px;
            transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.1);
        }

        .mic-btn:hover {
            background: rgba(255, 255, 255, 0.2);
            transform: scale(1.06);
        }

        .mic-btn.recording {
            background: rgba(255, 59, 48, 0.25);
            animation: pulse 1.8s ease-in-out infinite;
        }

        @keyframes pulse {
            0%, 100% { transform: scale(1); box-shadow: 0 4px 16px rgba(255, 59, 48, 0.2); }
            50% { transform: scale(1.08); box-shadow: 0 6px 24px rgba(255, 59, 48, 0.35); }
        }

        .status-text {
            text-align: center;
            color: rgba(255, 255, 255, 0.5);
            font-size: 13px;
            min-height: 20px;
        }

        .realtime-area {
            background: rgba(255, 255, 255, 0.05);
            backdrop-filter: blur(20px);
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 16px;
            padding: 16px 20px;
            min-height: 52px;
            color: rgba(255, 255, 255, 0.55);
            font-size: 16px;
            line-height: 1.5;
            font-style: italic;
            transition: all 0.2s ease;
        }

        .realtime-area.has-content {
            color: rgba(255, 255, 255, 0.75);
        }

        .result-area {
            background: rgba(255, 255, 255, 0.06);
            backdrop-filter: blur(20px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 16px;
            padding: 20px;
            min-height: 120px;
            color: #fff;
            font-size: 16px;
            line-height: 1.7;
            white-space: pre-wrap;
            word-break: break-word;
            transition: all 0.2s ease;
        }

        .btn-group {
            display: flex;
            gap: 12px;
            justify-content: center;
        }

        .btn {
            padding: 10px 28px;
            border-radius: 20px;
            border: 1px solid rgba(255, 255, 255, 0.12);
            background: rgba(255, 255, 255, 0.08);
            backdrop-filter: blur(20px);
            color: rgba(255, 255, 255, 0.85);
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            transition: all 0.2s ease;
        }

        .btn:hover {
            background: rgba(255, 255, 255, 0.15);
            transform: translateY(-1px);
        }

        .btn:active {
            transform: translateY(0);
        }

        .settings-toggle {
            text-align: center;
            color: rgba(255, 255, 255, 0.4);
            font-size: 12px;
            cursor: pointer;
            transition: color 0.2s;
        }

        .settings-toggle:hover {
            color: rgba(255, 255, 255, 0.7);
        }

        .settings-panel {
            display: none;
            background: rgba(255, 255, 255, 0.05);
            backdrop-filter: blur(20px);
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 16px;
            padding: 20px;
            gap: 14px;
            flex-direction: column;
        }

        .settings-panel.open {
            display: flex;
        }

        .setting-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            color: rgba(255, 255, 255, 0.75);
            font-size: 14px;
        }

        .toggle {
            width: 48px;
            height: 26px;
            border-radius: 13px;
            background: rgba(255, 255, 255, 0.15);
            position: relative;
            cursor: pointer;
            transition: background 0.25s ease;
            flex-shrink: 0;
        }

        .toggle.active {
            background: #34c759;
        }

        .toggle::after {
            content: '';
            position: absolute;
            width: 22px;
            height: 22px;
            border-radius: 50%;
            background: #fff;
            top: 2px;
            left: 2px;
            transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
        }

        .toggle.active::after {
            transform: translateX(22px);
        }
    </style>
</head>
<body>
    <div class="glass-panel">
        <div class="header">语音输入器</div>
        <div class="mic-wrapper">
            <button class="mic-btn" id="micBtn">🎙️</button>
        </div>
        <div class="status-text" id="statusText">点击麦克风开始录音</div>
        <div class="realtime-area" id="realtimeText"></div>
        <div class="result-area" id="resultArea"></div>
        <div class="btn-group">
            <button class="btn" id="copyBtn">复制</button>
            <button class="btn" id="clearBtn">清空</button>
        </div>
        <div class="settings-toggle" id="settingsToggle">⚙ 设置</div>
        <div class="settings-panel" id="settingsPanel">
            <div class="setting-row">
                <span>实时修正</span>
                <div class="toggle active" id="streamingToggle"></div>
            </div>
            <div class="setting-row">
                <span>后处理纠错</span>
                <div class="toggle" id="postToggle"></div>
            </div>
        </div>
    </div>
    <script src="voice-input.js"></script>
</body>
</html>
```

- [ ] **Step 3: Write voice-input.js**

Create `coloop-agent-voice/static/voice-input.js`:
```javascript
const micBtn = document.getElementById("micBtn");
const statusText = document.getElementById("statusText");
const realtimeText = document.getElementById("realtimeText");
const resultArea = document.getElementById("resultArea");
const settingsToggle = document.getElementById("settingsToggle");
const settingsPanel = document.getElementById("settingsPanel");
const streamingToggle = document.getElementById("streamingToggle");
const postToggle = document.getElementById("postToggle");

let socket = null;
let audioContext = null;
let mediaStream = null;
let workletNode = null;
let isRecording = false;

const SAMPLE_RATE = 16000;

async function startRecording() {
    try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: { sampleRate: SAMPLE_RATE, channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
        audioContext = new AudioContext({ sampleRate: SAMPLE_RATE });

        await audioContext.audioWorklet.addModule("audio-processor.js");

        const source = audioContext.createMediaStreamSource(mediaStream);
        workletNode = new AudioWorkletNode(audioContext, "pcm-processor");

        socket = io();

        socket.on("connect", () => {
            socket.emit("start", {
                config: {
                    lang: "zh",
                    enable_streaming_correction: streamingToggle.classList.contains("active"),
                    enable_post_correction: postToggle.classList.contains("active"),
                }
            });
            statusText.textContent = "正在录音...";
        });

        socket.on("partial", (data) => {
            realtimeText.textContent = data.text;
            realtimeText.classList.add("has-content");
        });

        socket.on("segment_final", (data) => {
            realtimeText.textContent = "";
            realtimeText.classList.remove("has-content");
            resultArea.textContent += data.text;
        });

        socket.on("post_corrected", (data) => {
            const current = resultArea.textContent;
            if (current.endsWith(data.original)) {
                resultArea.textContent = current.slice(0, -data.original.length) + data.text;
            }
        });

        socket.on("complete", (data) => {
            statusText.textContent = "录音完成";
        });

        socket.on("error", (data) => {
            statusText.textContent = "错误: " + data.message;
            console.error("Error:", data.message);
        });

        workletNode.port.onmessage = (e) => {
            if (socket && socket.connected) {
                socket.emit("audio", e.data);
            }
        };

        source.connect(workletNode);
        workletNode.connect(audioContext.destination);

        isRecording = true;
        micBtn.classList.add("recording");
        micBtn.textContent = "⏹️";

    } catch (err) {
        console.error("Failed to start recording:", err);
        statusText.textContent = "无法访问麦克风，请检查权限";
    }
}

function stopRecording() {
    if (socket) {
        socket.emit("stop");
        socket.disconnect();
        socket = null;
    }
    if (workletNode) {
        workletNode.disconnect();
        workletNode = null;
    }
    if (audioContext) {
        audioContext.close();
        audioContext = null;
    }
    if (mediaStream) {
        mediaStream.getTracks().forEach((t) => t.stop());
        mediaStream = null;
    }
    isRecording = false;
    micBtn.classList.remove("recording");
    micBtn.textContent = "🎙️";
    realtimeText.textContent = "";
    realtimeText.classList.remove("has-content");
}

micBtn.addEventListener("click", () => {
    if (isRecording) {
        stopRecording();
    } else {
        startRecording();
    }
});

document.getElementById("copyBtn").addEventListener("click", () => {
    navigator.clipboard
        .writeText(resultArea.textContent)
        .then(() => {
            statusText.textContent = "已复制到剪贴板";
            setTimeout(() => { if (!isRecording) statusText.textContent = "点击麦克风开始录音"; }, 2000);
        })
        .catch(() => statusText.textContent = "复制失败");
});

document.getElementById("clearBtn").addEventListener("click", () => {
    resultArea.textContent = "";
    statusText.textContent = "已清空";
    setTimeout(() => { if (!isRecording) statusText.textContent = "点击麦克风开始录音"; }, 1500);
});

settingsToggle.addEventListener("click", () => {
    settingsPanel.classList.toggle("open");
});

function setupToggle(el) {
    el.addEventListener("click", () => el.classList.toggle("active"));
}

setupToggle(streamingToggle);
setupToggle(postToggle);
```

- [ ] **Step 4: Commit**

```bash
git add coloop-agent-voice/static/
git commit -m "feat(voice): Apple liquid-glass UI with audio capture and Socket.IO"
```

---

## Task 10: README and Manual Verification

**Files:**
- Create: `coloop-agent-voice/README.md`

- [ ] **Step 1: Write README.md**

Create `coloop-agent-voice/README.md`:
```markdown
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

## 安装

```bash
cd coloop-agent-voice
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## 配置

复制 `.env.example` 为 `.env`，按需修改：

```bash
WHISPER_MODEL=base          # tiny/base/small/medium/large
WHISPER_DEVICE=cpu          # cpu 或 cuda
ENABLE_POST_CORRECTION=false
POST_CORRECTION_MODEL=minimax
SETTING_FILE=../coloop-agent-core/src/main/resources/coloop-agent-setting.json
```

后处理纠错复用现有 `coloop-agent-setting.json` 中的模型配置。

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
```

- [ ] **Step 2: Manual verification**

Run:
```bash
cd coloop-agent-voice
pip install -r requirements.txt
python main.py
```

Expected output:
```
INFO:     Started server process [PID]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
```

Open browser to `http://localhost:8000/static/voice-input.html` and verify:
1. Page loads with liquid-glass UI
2. Clicking microphone requests audio permission
3. Recording starts (button pulses red)
4. Stop button stops recording
5. Copy and Clear buttons work

- [ ] **Step 3: Commit**

```bash
git add coloop-agent-voice/README.md
git commit -m "docs(voice): README with setup and usage instructions"
```

---

## Self-Review

### Spec Coverage

| Spec Requirement | Implementing Task |
|---|---|
| 流式语音识别 | Task 7 (VoiceSession feed_audio), Task 8 (WebSocket audio handler) |
| 可配置纠错（段内+整体） | Task 3 (streaming_diff), Task 5 (PostCorrector), Task 7 (config switches) |
| 本地模型优先 | Task 4 (WhisperEngine), Task 6 (HTTP adapter stub) |
| 模块独立 | Entire plan — separate directory, own requirements, no Java dependencies |
| 模型配置复用 | Task 1 (config.py reads coloop-agent-setting.json) |
| Apple liquid-glass UI | Task 9 (voice-input.html CSS) |

### Placeholder Scan

- No TBD, TODO, or "implement later" found.
- All test files contain actual test code with assertions.
- All implementation files contain complete code.
- No vague references like "similar to Task X".

### Type Consistency

- `VoiceSession.emit_callback` signature: `Callable[[str, dict], None]` — consistent across Task 7 and Task 8.
- `VADProcessor.process()` returns `bytes | None` — consistent in Task 2.
- `WhisperEngine.transcribe()` signature: `(bytes, str) -> str` — consistent in Task 4.
- Socket.IO event names: `"start"`, `"audio"`, `"stop"`, `"partial"`, `"segment_final"`, `"post_corrected"`, `"complete"`, `"error"` — consistent across backend (Task 8) and frontend (Task 9).

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-01-voice-input.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
