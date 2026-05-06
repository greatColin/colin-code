# Voice Module Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `coloop-agent-voice` to use pluggable strategy patterns for transcription and correction, with config driven from `coloop-agent-setting.json`.

**Architecture:** Strategy pattern with two abstract base classes (`TranscriptionStrategy`, `CorrectionStrategy`). A `VoiceFactory` reads `coloop-agent-setting.json` and instantiates the configured strategies. `VoiceSession` depends on interfaces, not concrete classes.

**Tech Stack:** Python 3.10+, faster-whisper, httpx, FastAPI, python-socketio

**Spec:** `docs/superpowers/specs/2026-05-06-voice-module-refactor-design.md`

---

### Task 1: Core Interfaces

**Files:**
- Create: `coloop-agent-voice/core/__init__.py`
- Create: `coloop-agent-voice/core/transcription_strategy.py`
- Create: `coloop-agent-voice/core/correction_strategy.py`

- [ ] **Step 1: Create core package**

```bash
mkdir -p coloop-agent-voice/core
```

- [ ] **Step 2: Write TranscriptionStrategy interface**

Create `coloop-agent-voice/core/transcription_strategy.py`:

```python
from abc import ABC, abstractmethod


class TranscriptionStrategy(ABC):
    """语音转文字策略接口"""

    @abstractmethod
    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        """将 PCM int16 音频字节转为文本"""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """策略名称，用于日志和配置标识"""
        ...
```

- [ ] **Step 3: Write CorrectionStrategy interface**

Create `coloop-agent-voice/core/correction_strategy.py`:

```python
from abc import ABC, abstractmethod


class CorrectionStrategy(ABC):
    """文本纠错策略接口"""

    @abstractmethod
    async def correct(self, text: str) -> str:
        """对识别文本进行纠错"""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """策略名称，用于日志和配置标识"""
        ...
```

- [ ] **Step 4: Write __init__.py**

Create `coloop-agent-voice/core/__init__.py`:

```python
from .transcription_strategy import TranscriptionStrategy
from .correction_strategy import CorrectionStrategy

__all__ = ["TranscriptionStrategy", "CorrectionStrategy"]
```

- [ ] **Step 5: Commit**

```bash
cd coloop-agent-voice
git add core/
git commit -m "feat(voice): add core strategy interfaces"
```

---

### Task 2: NoOpCorrectionStrategy

**Files:**
- Create: `coloop-agent-voice/correction/no_op_corrector.py`
- Create: `coloop-agent-voice/tests/test_no_op_corrector.py`

- [ ] **Step 1: Write the failing test**

Create `coloop-agent-voice/tests/test_no_op_corrector.py`:

```python
import pytest
from correction.no_op_corrector import NoOpCorrectionStrategy


@pytest.mark.asyncio
async def test_correct_returns_text_unchanged():
    s = NoOpCorrectionStrategy()
    result = await s.correct("你好世界")
    assert result == "你好世界"


@pytest.mark.asyncio
async def test_correct_returns_empty_string():
    s = NoOpCorrectionStrategy()
    result = await s.correct("")
    assert result == ""


def test_get_name():
    s = NoOpCorrectionStrategy()
    assert s.get_name() == "none"
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd coloop-agent-voice && python -m pytest tests/test_no_op_corrector.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'correction.no_op_corrector'`

- [ ] **Step 3: Write implementation**

Create `coloop-agent-voice/correction/no_op_corrector.py`:

```python
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from core.correction_strategy import CorrectionStrategy


class NoOpCorrectionStrategy(CorrectionStrategy):
    """不纠错，直接返回原文"""

    async def correct(self, text: str) -> str:
        return text

    def get_name(self) -> str:
        return "none"
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd coloop-agent-voice && python -m pytest tests/test_no_op_corrector.py -v
```

Expected: 3 passed

- [ ] **Step 5: Commit**

```bash
cd coloop-agent-voice
git add correction/no_op_corrector.py tests/test_no_op_corrector.py
git commit -m "feat(voice): add NoOpCorrectionStrategy"
```

---

### Task 3: Refactor WhisperEngine to Implement TranscriptionStrategy

**Files:**
- Modify: `coloop-agent-voice/engine/whisper_engine.py`

- [ ] **Step 1: Update WhisperEngine to implement TranscriptionStrategy**

Replace `coloop-agent-voice/engine/whisper_engine.py`:

```python
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np
from faster_whisper import WhisperModel
from core.transcription_strategy import TranscriptionStrategy


class LocalWhisperStrategy(TranscriptionStrategy):
    def __init__(self, model: str = "base", device: str = "cpu", compute_type: str = "int8", model_dir: str = "./models"):
        model_path = os.path.join(model_dir, model) if os.path.isdir(os.path.join(model_dir, model)) else model
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

    def get_name(self) -> str:
        return "local_whisper"


# Backward compatibility alias
WhisperEngine = LocalWhisperStrategy
```

- [ ] **Step 2: Verify existing tests still pass**

```bash
cd coloop-agent-voice && python -m pytest tests/test_whisper_engine.py -v
```

Expected: existing tests pass (they use WhisperEngine alias)

- [ ] **Step 3: Commit**

```bash
cd coloop-agent-voice
git add engine/whisper_engine.py
git commit -m "refactor(voice): WhisperEngine implements TranscriptionStrategy"
```

---

### Task 4: Refactor PostCorrector to Implement CorrectionStrategy

**Files:**
- Modify: `coloop-agent-voice/correction/post_corrector.py`

- [ ] **Step 1: Update PostCorrector to implement CorrectionStrategy**

Replace `coloop-agent-voice/correction/post_corrector.py`:

```python
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import httpx
from core.correction_strategy import CorrectionStrategy


class LLMCorrectionStrategy(CorrectionStrategy):
    """LLM 纠错策略"""

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

    def get_name(self) -> str:
        return "llm"


# Backward compatibility alias
PostCorrector = LLMCorrectionStrategy
```

- [ ] **Step 2: Verify existing tests still pass**

```bash
cd coloop-agent-voice && python -m pytest tests/test_post_corrector.py -v
```

Expected: existing tests pass (they use PostCorrector alias)

- [ ] **Step 3: Commit**

```bash
cd coloop-agent-voice
git add correction/post_corrector.py
git commit -m "refactor(voice): PostCorrector implements CorrectionStrategy"
```

---

### Task 5: HttpTranscriptionStrategy

**Files:**
- Modify: `coloop-agent-voice/engine/http_adapter.py`
- Create: `coloop-agent-voice/tests/test_http_adapter.py`

- [ ] **Step 1: Write the failing test**

Create `coloop-agent-voice/tests/test_http_adapter.py`:

```python
import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from engine.http_adapter import HttpTranscriptionStrategy


def test_get_name():
    s = HttpTranscriptionStrategy(api_url="https://api.example.com/asr", api_key="test-key")
    assert s.get_name() == "http_api"


def test_init_strips_trailing_slash():
    s = HttpTranscriptionStrategy(api_url="https://api.example.com/asr/", api_key="test-key")
    assert s.api_url == "https://api.example.com/asr"


@patch("engine.http_adapter.httpx")
def test_transcribe_calls_api(mock_httpx):
    mock_response = MagicMock()
    mock_response.json.return_value = {"text": "你好世界"}
    mock_response.raise_for_status = MagicMock()

    mock_client = MagicMock()
    mock_client.post.return_value = mock_response
    mock_client.__enter__ = MagicMock(return_value=mock_client)
    mock_client.__exit__ = MagicMock(return_value=False)
    mock_httpx.Client.return_value = mock_client

    s = HttpTranscriptionStrategy(api_url="https://api.example.com/asr", api_key="test-key")
    result = s.transcribe(b"\x00\x00" * 100, language="zh")

    assert result == "你好世界"
    mock_client.post.assert_called_once()
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd coloop-agent-voice && python -m pytest tests/test_http_adapter.py -v
```

Expected: FAIL with `ImportError: cannot import name 'HttpTranscriptionStrategy'`

- [ ] **Step 3: Write implementation**

Replace `coloop-agent-voice/engine/http_adapter.py`:

```python
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import httpx
import base64
from core.transcription_strategy import TranscriptionStrategy


class HttpTranscriptionStrategy(TranscriptionStrategy):
    """通过 HTTP 调用外部 ASR API 的转写策略"""

    def __init__(self, api_url: str, api_key: str, model: str = None):
        self.api_url = api_url.rstrip("/")
        self.api_key = api_key
        self.model = model

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        audio_b64 = base64.b64encode(audio_bytes).decode("utf-8")
        payload = {
            "audio": audio_b64,
            "language": language,
        }
        if self.model:
            payload["model"] = self.model

        with httpx.Client(timeout=30.0) as client:
            response = client.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
            return data.get("text", "").strip()

    def get_name(self) -> str:
        return "http_api"
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd coloop-agent-voice && python -m pytest tests/test_http_adapter.py -v
```

Expected: 3 passed

- [ ] **Step 5: Commit**

```bash
cd coloop-agent-voice
git add engine/http_adapter.py tests/test_http_adapter.py
git commit -m "feat(voice): implement HttpTranscriptionStrategy"
```

---

### Task 6: WebSocketTranscriptionStrategy

**Files:**
- Create: `coloop-agent-voice/engine/websocket_adapter.py`
- Create: `coloop-agent-voice/tests/test_websocket_adapter.py`

- [ ] **Step 1: Write the failing test**

Create `coloop-agent-voice/tests/test_websocket_adapter.py`:

```python
import pytest
from engine.websocket_adapter import WebSocketTranscriptionStrategy


def test_get_name():
    s = WebSocketTranscriptionStrategy(ws_url="wss://asr.example.com/ws")
    assert s.get_name() == "websocket"


def test_init_with_api_key():
    s = WebSocketTranscriptionStrategy(ws_url="wss://asr.example.com/ws", api_key="test-key")
    assert s.ws_url == "wss://asr.example.com/ws"
    assert s.api_key == "test-key"
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd coloop-agent-voice && python -m pytest tests/test_websocket_adapter.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'engine.websocket_adapter'`

- [ ] **Step 3: Write implementation**

Create `coloop-agent-voice/engine/websocket_adapter.py`:

```python
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import json
import base64
import websocket
from core.transcription_strategy import TranscriptionStrategy


class WebSocketTranscriptionStrategy(TranscriptionStrategy):
    """通过 WebSocket 流式调用 ASR 服务的转写策略"""

    def __init__(self, ws_url: str, api_key: str = None):
        self.ws_url = ws_url
        self.api_key = api_key

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        ws = websocket.create_connection(self.ws_url, header=headers, timeout=30)
        try:
            # Send audio as base64 JSON payload
            payload = {
                "audio": base64.b64encode(audio_bytes).decode("utf-8"),
                "language": language,
                "is_last": True,
            }
            ws.send(json.dumps(payload))

            # Collect results until final
            texts = []
            while True:
                raw = ws.recv()
                data = json.loads(raw)
                if "text" in data:
                    texts.append(data["text"])
                if data.get("is_final", True):
                    break
            return "".join(texts).strip()
        finally:
            ws.close()

    def get_name(self) -> str:
        return "websocket"
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd coloop-agent-voice && python -m pytest tests/test_websocket_adapter.py -v
```

Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
cd coloop-agent-voice
git add engine/websocket_adapter.py tests/test_websocket_adapter.py
git commit -m "feat(voice): add WebSocketTranscriptionStrategy"
```

---

### Task 7: Rewrite VoiceConfig

**Files:**
- Modify: `coloop-agent-voice/config.py`

- [ ] **Step 1: Rewrite VoiceConfig**

Replace `coloop-agent-voice/config.py`:

```python
import json
import re
import os
from pathlib import Path
from typing import Any, Dict, Optional


class VoiceConfig:
    """Voice module configuration. Reads from coloop-agent-setting.json."""

    DEFAULTS = {
        "host": "0.0.0.0",
        "port": 8000,
        "language": "zh",
        "enableStreamingCorrection": True,
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

- [ ] **Step 2: Verify existing tests still pass**

```bash
cd coloop-agent-voice && python -m pytest tests/test_config.py -v
```

Expected: tests may need adaptation (Task 12), but no import errors

- [ ] **Step 3: Commit**

```bash
cd coloop-agent-voice
git add config.py
git commit -m "refactor(voice): rewrite VoiceConfig to read from coloop-agent-setting.json"
```

---

### Task 8: VoiceFactory

**Files:**
- Create: `coloop-agent-voice/factory.py`
- Create: `coloop-agent-voice/tests/test_factory.py`

- [ ] **Step 1: Write the failing test**

Create `coloop-agent-voice/tests/test_factory.py`:

```python
import json
import pytest
import tempfile
import os
from factory import VoiceFactory
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from correction.no_op_corrector import NoOpCorrectionStrategy


def _write_config(tmp_dir, config_dict):
    path = os.path.join(tmp_dir, "test-setting.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(config_dict, f)
    return path


def test_create_correction_noop():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {"voice": {"correction": {"strategy": "none"}}})
        factory = VoiceFactory(setting_file=path)
        correction = factory.create_correction()
        assert isinstance(correction, CorrectionStrategy)
        assert correction.get_name() == "none"


def test_create_correction_default_is_noop():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        correction = factory.create_correction()
        assert correction.get_name() == "none"


def test_create_transcription_default_is_whisper():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        # Will fail to actually load whisper model, but verifies strategy selection
        assert factory.config.get_transcription_strategy_name() == "local_whisper"


def test_config_reads_voice_field():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {"voice": {"language": "en", "enableStreamingCorrection": False}})
        factory = VoiceFactory(setting_file=path)
        assert factory.config.get("language") == "en"
        assert factory.config.get("enableStreamingCorrection") is False


def test_config_defaults():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        assert factory.config.get("language") == "zh"
        assert factory.config.get("host") == "0.0.0.0"
        assert factory.config.get("port") == 8000
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd coloop-agent-voice && python -m pytest tests/test_factory.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'factory'`

- [ ] **Step 3: Write implementation**

Create `coloop-agent-voice/factory.py`:

```python
from config import VoiceConfig
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from correction.no_op_corrector import NoOpCorrectionStrategy


class VoiceFactory:
    """根据配置创建策略实例并组装 VoiceSession"""

    def __init__(self, setting_file: str = None):
        self.config = VoiceConfig(setting_file)

    def create_transcription(self) -> TranscriptionStrategy:
        name = self.config.get_transcription_strategy_name()
        params = self.config.get_transcription_params(name)

        if name == "local_whisper":
            from engine.whisper_engine import LocalWhisperStrategy
            return LocalWhisperStrategy(
                model=params.get("model", "base"),
                device=params.get("device", "cpu"),
                compute_type=params.get("computeType", "int8"),
                model_dir=params.get("modelDir", "./models"),
            )
        elif name == "http_api":
            from engine.http_adapter import HttpTranscriptionStrategy
            return HttpTranscriptionStrategy(
                api_url=params.get("apiUrl", ""),
                api_key=params.get("apiKey", ""),
                model=params.get("model"),
            )
        elif name == "websocket":
            from engine.websocket_adapter import WebSocketTranscriptionStrategy
            return WebSocketTranscriptionStrategy(
                ws_url=params.get("wsUrl", ""),
                api_key=params.get("apiKey"),
            )
        else:
            raise ValueError(f"Unknown transcription strategy: {name}")

    def create_correction(self) -> CorrectionStrategy:
        name = self.config.get_correction_strategy_name()
        params = self.config.get_correction_params(name)

        if name == "llm":
            from correction.post_corrector import LLMCorrectionStrategy
            # Resolve model config from models section
            model_name = params.get("model", "minimax")
            model_cfg = self.config.get_model_config(model_name)
            if model_cfg:
                return LLMCorrectionStrategy(
                    api_base=model_cfg.get("apiBase", ""),
                    api_key=model_cfg.get("apiKey", ""),
                    model=model_cfg.get("model", ""),
                )
            else:
                raise ValueError(f"Model '{model_name}' not found in models config")
        elif name == "none":
            return NoOpCorrectionStrategy()
        else:
            raise ValueError(f"Unknown correction strategy: {name}")
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd coloop-agent-voice && python -m pytest tests/test_factory.py -v
```

Expected: 5 passed

- [ ] **Step 5: Commit**

```bash
cd coloop-agent-voice
git add factory.py tests/test_factory.py
git commit -m "feat(voice): add VoiceFactory for config-driven strategy creation"
```

---

### Task 9: Refactor VoiceSession for Dependency Injection

**Files:**
- Modify: `coloop-agent-voice/session/voice_session.py`

- [ ] **Step 1: Rewrite VoiceSession**

Replace `coloop-agent-voice/session/voice_session.py`:

```python
import asyncio
import time
from typing import Optional, Callable

from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from audio.energy_vad import EnergyVAD
from correction.streaming_diff import streaming_diff


class VoiceSession:
    def __init__(
        self,
        transcription_strategy: TranscriptionStrategy,
        correction_strategy: CorrectionStrategy,
        emit_callback: Optional[Callable] = None,
        language: str = "zh",
        enable_streaming_correction: bool = True,
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

    async def feed_audio(self, pcm_bytes: bytes):
        async with self._lock:
            segment = self.vad.process(pcm_bytes)
            if segment:
                await self._finalize_segment_audio(segment)

            if self.vad.triggered:
                now = time.monotonic()
                if now - self._last_preview_time >= self._preview_interval:
                    preview = self.vad.current_segment
                    if len(preview) >= 16000 * 1 * 2:  # at least 1 second
                        await self._preview_transcribe(preview)
                        self._last_preview_time = now

    async def _preview_transcribe(self, audio_bytes: bytes):
        try:
            text = self.transcription.transcribe(audio_bytes, language=self.language)
            if not text:
                return
            if self.enable_streaming_correction:
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
                if text != self.last_text:
                    await self.emit(
                        "partial",
                        {
                            "text": text,
                            "segment_index": self.segment_index,
                            "is_stable": False,
                        },
                    )
                    self.last_text = text
        except Exception as e:
            print(f"[_preview_transcribe] error: {e}")

    async def _finalize_segment_audio(self, audio_bytes: bytes):
        try:
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

    async def finalize_segment(self):
        async with self._lock:
            segment = self.vad.flush()
            if segment:
                await self._finalize_segment_audio(segment)

    async def stop(self):
        await self.finalize_segment()
        await self.emit("complete", {"full_text": self.full_text.strip()})
```

- [ ] **Step 2: Verify existing tests still pass**

```bash
cd coloop-agent-voice && python -m pytest tests/test_voice_session.py -v
```

Expected: tests may need adaptation (Task 12)

- [ ] **Step 3: Commit**

```bash
cd coloop-agent-voice
git add session/voice_session.py
git commit -m "refactor(voice): VoiceSession uses dependency injection"
```

---

### Task 10: Refactor main.py to Use Factory

**Files:**
- Modify: `coloop-agent-voice/main.py`

- [ ] **Step 1: Rewrite main.py**

Replace `coloop-agent-voice/main.py`:

```python
import asyncio
import socketio
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from factory import VoiceFactory
from session.voice_session import VoiceSession

# Default setting file path
DEFAULT_SETTING_FILE = "../coloop-agent-core/src/main/resources/coloop-agent-setting.json"

factory = VoiceFactory(setting_file=DEFAULT_SETTING_FILE)
sio = socketio.AsyncServer(
    async_mode="asgi",
    cors_allowed_origins="*",
    max_http_buffer_size=10_000_000,
    ping_timeout=60,
    ping_interval=25,
)
app = FastAPI()
socket_app = socketio.ASGIApp(sio, app)

# Static files
app.mount("/static", StaticFiles(directory="static"), name="static")

sessions = {}

# Pre-create transcription strategy (may need lazy loading for heavy models)
_transcription_strategy = None
_transcription_lock = asyncio.Lock()


async def get_transcription_strategy():
    global _transcription_strategy
    if _transcription_strategy is None:
        async with _transcription_lock:
            if _transcription_strategy is None:
                print("[strategy] creating transcription strategy, please wait...")
                loop = asyncio.get_event_loop()
                _transcription_strategy = await loop.run_in_executor(
                    None, factory.create_transcription
                )
                print(f"[strategy] ready: {_transcription_strategy.get_name()}")
    return _transcription_strategy


@sio.event
async def connect(sid, environ):
    print(f"Client connected: {sid}")


@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")
    sessions.pop(sid, None)


@sio.on("start")
async def on_start(sid, data):
    transcription = await get_transcription_strategy()
    correction = factory.create_correction()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session_config = data.get("config", {})
    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        language=factory.config.get("language"),
        enable_streaming_correction=factory.config.get("enableStreamingCorrection"),
        vad_threshold=session_config.get("vad_threshold", 500),
        silence_timeout_ms=session_config.get("silence_timeout_ms", 1000),
        max_segment_ms=session_config.get("max_segment_ms", 15000),
        preview_interval_sec=session_config.get("preview_interval_sec", 1.5),
    )
    sessions[sid] = session


@sio.on("audio")
async def on_audio(sid, data):
    if sid in sessions:
        print(f"[audio] sid={sid[:8]} len={len(data)}")
        await sessions[sid].feed_audio(data)
    else:
        print(f"[audio] no session for sid={sid[:8]}")


@sio.on("stop")
async def on_stop(sid, data=None):
    session = sessions.pop(sid, None)
    if session:
        await session.stop()


@app.on_event("startup")
async def startup_event():
    print("[startup] preloading transcription strategy...")
    await get_transcription_strategy()
    print("[startup] ready")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(socket_app, host=factory.config.host, port=factory.config.port)
```

- [ ] **Step 2: Commit**

```bash
cd coloop-agent-voice
git add main.py
git commit -m "refactor(voice): main.py uses VoiceFactory for strategy assembly"
```

---

### Task 11: Update coloop-agent-setting.json

**Files:**
- Modify: `coloop-agent-core/src/main/resources/coloop-agent-setting.json`

- [ ] **Step 1: Add voice config field**

Add the `voice` field to `coloop-agent-core/src/main/resources/coloop-agent-setting.json`:

```json
{
  "maxIterations": 50,
  "execTimeoutSeconds": 30,
  "defaultModel": "minimax",
  "models": {
    "openai": {
      "apiKey": "${COLIN_CODE_OPENAI_API_KEY}",
      "apiBase": "${COLIN_CODE_OPENAI_API_BASE}",
      "model": "${COLIN_CODE_OPENAI_MODEL}"
    },
    "glm-4-free": {
      "apiKey": "${COLIN_CODE_GLM_API_KEY}",
      "apiBase": "https://open.bigmodel.cn/api/paas/v4",
      "model": "GLM-4.7-Flash",
      "maxContextSize": "100k"
    },
    "minimax": {
      "apiKey": "${COLIN_CODE_MINIMAX_API_KEY}",
      "apiBase": "https://api.minimaxi.com/v1",
      "model": "MiniMax-M2.7",
      "maxContextSize": "200k"
    }
  },
  "mcpServers": {
    "MiniMax": {
      "description": "此处可以放mcp,例如miniMax的搜索mcp",
      "command": "uvx",
      "args": ["minimax-coding-plan-mcp"],
      "env": {
        "MINIMAX_API_KEY": "${models.minimax.apiKey}",
        "MINIMAX_MCP_BASE_PATH": "/minimaxBase",
        "MINIMAX_API_HOST": "https://api.minimaxi.com"
      }
    }
  },
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
      "strategy": "none",
      "strategies": {
        "llm": {
          "model": "minimax"
        },
        "none": {}
      }
    },
    "language": "zh",
    "enableStreamingCorrection": true
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add coloop-agent-core/src/main/resources/coloop-agent-setting.json
git commit -m "feat(config): add voice configuration to coloop-agent-setting.json"
```

---

### Task 12: Clean Up and Adapt Tests

**Files:**
- Delete: `coloop-agent-voice/.env`
- Delete: `coloop-agent-voice/.env.example`
- Modify: `coloop-agent-voice/requirements.txt`
- Modify: `coloop-agent-voice/tests/test_config.py`
- Modify: `coloop-agent-voice/tests/test_voice_session.py`
- Modify: `coloop-agent-voice/tests/test_whisper_engine.py`
- Modify: `coloop-agent-voice/tests/test_post_corrector.py`

- [ ] **Step 1: Remove .env files**

```bash
cd coloop-agent-voice
rm .env .env.example
```

- [ ] **Step 2: Update requirements.txt — remove python-dotenv**

Remove the line `python-dotenv>=1.0.0` from `coloop-agent-voice/requirements.txt`.

- [ ] **Step 3: Adapt test_config.py**

Replace `coloop-agent-voice/tests/test_config.py`:

```python
import json
import os
import tempfile
import pytest
from config import VoiceConfig


def test_defaults_when_no_file():
    config = VoiceConfig()
    assert config.get("host") == "0.0.0.0"
    assert config.get("port") == 8000
    assert config.get("language") == "zh"
    assert config.get("enableStreamingCorrection") is True


def test_defaults_when_empty_config():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({}, f)
        config = VoiceConfig(setting_file=path)
        assert config.get("language") == "zh"
        assert config.get_transcription_strategy_name() == "local_whisper"
        assert config.get_correction_strategy_name() == "none"


def test_voice_config_override():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({"voice": {"language": "en", "port": 9000}}, f)
        config = VoiceConfig(setting_file=path)
        assert config.get("language") == "en"
        assert config.get("port") == 9000


def test_transcription_params():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({
                "voice": {
                    "transcription": {
                        "strategy": "http_api",
                        "strategies": {
                            "http_api": {"apiUrl": "https://example.com", "apiKey": "key123"}
                        }
                    }
                }
            }, f)
        config = VoiceConfig(setting_file=path)
        assert config.get_transcription_strategy_name() == "http_api"
        params = config.get_transcription_params("http_api")
        assert params["apiUrl"] == "https://example.com"


def test_env_var_expansion():
    os.environ["TEST_ASR_KEY"] = "expanded-value"
    try:
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "setting.json")
            with open(path, "w") as f:
                json.dump({
                    "voice": {
                        "transcription": {
                            "strategy": "http_api",
                            "strategies": {
                                "http_api": {"apiUrl": "https://example.com", "apiKey": "${TEST_ASR_KEY}"}
                            }
                        }
                    }
                }, f)
            config = VoiceConfig(setting_file=path)
            params = config.get_transcription_params("http_api")
            assert params["apiKey"] == "expanded-value"
    finally:
        del os.environ["TEST_ASR_KEY"]


def test_missing_file_uses_defaults():
    config = VoiceConfig(setting_file="/nonexistent/path.json")
    assert config.get("language") == "zh"
```

- [ ] **Step 4: Adapt test_voice_session.py**

Replace `coloop-agent-voice/tests/test_voice_session.py`:

```python
import pytest
import asyncio
from unittest.mock import MagicMock, AsyncMock
from session.voice_session import VoiceSession
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy


class MockTranscription(TranscriptionStrategy):
    def __init__(self, result="hello"):
        self.result = result
        self.calls = []

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        self.calls.append(("transcribe", audio_bytes, language))
        return self.result

    def get_name(self) -> str:
        return "mock"


class MockCorrection(CorrectionStrategy):
    def __init__(self, result="corrected"):
        self.result = result

    async def correct(self, text: str) -> str:
        return self.result

    def get_name(self) -> str:
        return "mock"


@pytest.mark.asyncio
async def test_session_uses_injected_strategies():
    transcription = MockTranscription(result="你好")
    correction = MockCorrection(result="你好。")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        language="zh",
        enable_streaming_correction=False,
    )

    # Feed enough audio to trigger finalize (simulate silence after speech)
    # We'll directly call _finalize_segment_audio with dummy audio
    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    # Should have called transcription
    assert len(transcription.calls) == 1

    # Should have emitted segment_final and post_corrected
    events = [e[0] for e in emitted]
    assert "segment_final" in events
    assert "post_corrected" in events


@pytest.mark.asyncio
async def test_session_stop_emits_complete():
    transcription = MockTranscription(result="")
    correction = MockCorrection()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
    )
    session.full_text = "hello world "
    await session.stop()

    assert ("complete", {"full_text": "hello world"}) in emitted
```

- [ ] **Step 5: Adapt test_whisper_engine.py**

Update import in `coloop-agent-voice/tests/test_whisper_engine.py`:

Change `from engine.whisper_engine import WhisperEngine` to `from engine.whisper_engine import LocalWhisperStrategy, WhisperEngine`

The `WhisperEngine` alias should keep existing tests working.

- [ ] **Step 6: Adapt test_post_corrector.py**

Update import in `coloop-agent-voice/tests/test_post_corrector.py`:

Change `from correction.post_corrector import PostCorrector` to `from correction.post_corrector import LLMCorrectionStrategy, PostCorrector`

The `PostCorrector` alias should keep existing tests working.

- [ ] **Step 7: Run all tests**

```bash
cd coloop-agent-voice && python -m pytest tests/ -v
```

Expected: all tests pass

- [ ] **Step 8: Commit**

```bash
cd coloop-agent-voice
git add -A
git commit -m "chore(voice): remove .env, adapt tests for strategy pattern"
```

---

### Task 13: Final Verification

- [ ] **Step 1: Run all tests from project root**

```bash
cd coloop-agent-voice && python -m pytest tests/ -v --tb=short
```

Expected: all tests pass

- [ ] **Step 2: Verify imports work end-to-end**

```bash
cd coloop-agent-voice && python -c "
from factory import VoiceFactory
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from correction.no_op_corrector import NoOpCorrectionStrategy
from engine.http_adapter import HttpTranscriptionStrategy
from engine.websocket_adapter import WebSocketTranscriptionStrategy
print('All imports OK')
"
```

Expected: `All imports OK`

- [ ] **Step 3: Verify config loads correctly**

```bash
cd coloop-agent-voice && python -c "
from config import VoiceConfig
config = VoiceConfig(setting_file='../coloop-agent-core/src/main/resources/coloop-agent-setting.json')
print(f'transcription: {config.get_transcription_strategy_name()}')
print(f'correction: {config.get_correction_strategy_name()}')
print(f'language: {config.get(\"language\")}')
"
```

Expected output:
```
transcription: local_whisper
correction: none
language: zh
```

- [ ] **Step 4: Commit any remaining changes**

```bash
cd coloop-agent-voice && git status
```

If clean, no commit needed.
