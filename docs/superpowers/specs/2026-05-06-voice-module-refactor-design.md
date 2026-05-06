# Voice Module Refactoring Design

## Overview

Refactor `coloop-agent-voice` to align with the project's onion architecture by introducing pluggable strategy patterns for transcription (ASR) and correction (LLM post-processing). Configuration migrates from `.env` to `coloop-agent-setting.json`.

## Goals

1. Transcription (speech-to-text) becomes a pluggable strategy, supporting multiple backends
2. LLM correction becomes a pluggable strategy
3. Configuration reads from `coloop-agent-setting.json` via a new `voice` top-level field
4. Remove `.env` dependency; defaults live in the config class

## Non-Goals

- Porting voice module to Java
- Adding new VAD implementations (existing EnergyVAD stays)
- Changing the frontend or WebSocket protocol

## Architecture

### Core Interfaces

**TranscriptionStrategy** (`core/transcription_strategy.py`):

```python
from abc import ABC, abstractmethod

class TranscriptionStrategy(ABC):
    @abstractmethod
    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str: ...

    @abstractmethod
    def get_name(self) -> str: ...
```

**CorrectionStrategy** (`core/correction_strategy.py`):

```python
from abc import ABC, abstractmethod

class CorrectionStrategy(ABC):
    @abstractmethod
    async def correct(self, text: str) -> str: ...

    @abstractmethod
    def get_name(self) -> str: ...
```

- `transcribe()` is synchronous (CPU-bound local inference)
- `correct()` is async (I/O-bound LLM API calls)

### Strategy Implementations

#### Transcription Strategies

| Class | File | strategy key | Description |
|-------|------|-------------|-------------|
| `LocalWhisperStrategy` | `engine/whisper_engine.py` | `local_whisper` | Current faster-whisper wrapper (default) |
| `HttpTranscriptionStrategy` | `engine/http_adapter.py` | `http_api` | HTTP POST to external ASR API |
| `WebSocketTranscriptionStrategy` | `engine/websocket_adapter.py` | `websocket` | WebSocket streaming ASR (e.g. FunASR) |

#### Correction Strategies

| Class | File | strategy key | Description |
|-------|------|-------------|-------------|
| `LLMCorrectionStrategy` | `correction/post_corrector.py` | `llm` | LLM-based homophone/punctuation correction |
| `NoOpCorrectionStrategy` | `correction/no_op_corrector.py` | `none` | Pass-through, returns original text |

### Configuration

`coloop-agent-setting.json` new `voice` field:

```json
{
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
          "apiUrl": "https://api.example.com/asr",
          "apiKey": "${ASR_API_KEY}",
          "model": "whisper-1"
        },
        "websocket": {
          "wsUrl": "wss://asr.example.com/ws",
          "apiKey": "${ASR_API_KEY}"
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

- `strategy` selects which implementation to use
- `strategies` holds per-implementation parameters
- `${VAR_NAME}` syntax for environment variable expansion
- Missing fields fall back to defaults in `VoiceConfig.DEFAULTS`

### VoiceConfig

Rewritten to read from `coloop-agent-setting.json` only. Only manages non-strategy settings; strategy-specific params are read by the factory directly from the nested JSON.

```python
class VoiceConfig:
    DEFAULTS = {
        "host": "0.0.0.0",
        "port": 8000,
        "language": "zh",
        "enable_streaming_correction": True,
    }

    def __init__(self, setting_file: str = None):
        self._raw = self._load_json(setting_file)
        self._voice = self._raw.get("voice", {})

    def get(self, key: str):
        """Non-strategy config: host, port, language, etc."""
        return self._voice.get(key, self.DEFAULTS.get(key))

    def get_transcription_strategy_name(self) -> str:
        return self._voice.get("transcription", {}).get("strategy", "local_whisper")

    def get_transcription_params(self, strategy_name: str) -> dict:
        return self._voice.get("transcription", {}).get("strategies", {}).get(strategy_name, {})

    def get_correction_strategy_name(self) -> str:
        return self._voice.get("correction", {}).get("strategy", "none")

    def get_correction_params(self, strategy_name: str) -> dict:
        return self._voice.get("correction", {}).get("strategies", {}).get(strategy_name, {})
```

No `.env` dependency. `python-dotenv` removed from requirements.

### VoiceFactory

`factory.py` — creates strategy instances from config:

```python
class VoiceFactory:
    def __init__(self, setting_file: str = None):
        self.config = VoiceConfig(setting_file)

    def create_transcription(self) -> TranscriptionStrategy:
        name = self.config.get_transcription_strategy_name()
        params = self.config.get_transcription_params(name)
        match name:
            case "local_whisper": return LocalWhisperStrategy(**params)
            case "http_api": return HttpTranscriptionStrategy(**params)
            case "websocket": return WebSocketTranscriptionStrategy(**params)

    def create_correction(self) -> CorrectionStrategy:
        name = self.config.get_correction_strategy_name()
        params = self.config.get_correction_params(name)
        match name:
            case "llm": return LLMCorrectionStrategy(params.get("model", "minimax"))
            case "none": return NoOpCorrectionStrategy()

    def create_session(self, emit_fn) -> VoiceSession:
        transcription = self.create_transcription()
        correction = self.create_correction()
        return VoiceSession(
            transcription_strategy=transcription,
            correction_strategy=correction,
            emit_fn=emit_fn,
            language=self.config.get("language"),
            enable_streaming_correction=self.config.get("enable_streaming_correction"),
        )
```

### VoiceSession

Refactored to dependency injection:

```python
class VoiceSession:
    def __init__(
        self,
        transcription_strategy: TranscriptionStrategy,
        correction_strategy: CorrectionStrategy,
        emit_fn,
        language="zh",
        enable_streaming_correction=True,
    ):
        self.transcription = transcription_strategy
        self.correction = correction_strategy
        # EnergyVAD creation stays internal (not pluggable)
```

- Calls `self.transcription.transcribe(audio, language)` instead of `whisper_engine.transcribe()`
- Calls `self.correction.correct(text)` instead of `post_corrector.correct()`
- VAD remains internal (not part of the strategy pattern)

### Data Flow

```
main.py
  → VoiceFactory(setting_file)
    → VoiceConfig(setting_file)  [reads coloop-agent-setting.json]
    → create_transcription()     [strategy pattern]
    → create_correction()        [strategy pattern]
    → create_session(emit_fn)    [dependency injection]
      → VoiceSession(transcription, correction, ...)
        → EnergyVAD.process(pcm)           [internal, not pluggable]
        → self.transcription.transcribe()   [via interface]
        → streaming_diff()                  [unchanged]
        → self.correction.correct()         [via interface]
```

## File Structure (After)

```
coloop-agent-voice/
├── core/                              # Abstract interfaces
│   ├── __init__.py
│   ├── transcription_strategy.py
│   └── correction_strategy.py
├── engine/                            # Transcription implementations
│   ├── __init__.py
│   ├── whisper_engine.py              # LocalWhisperStrategy (refactored)
│   ├── http_adapter.py                # HttpTranscriptionStrategy (completed)
│   └── websocket_adapter.py           # WebSocketTranscriptionStrategy (new)
├── audio/                             # VAD (unchanged)
│   ├── __init__.py
│   ├── vad_processor.py
│   └── energy_vad.py
├── correction/                        # Correction implementations
│   ├── __init__.py
│   ├── post_corrector.py              # LLMCorrectionStrategy (refactored)
│   ├── no_op_corrector.py             # NoOpCorrectionStrategy (new)
│   └── streaming_diff.py              # Unchanged
├── session/
│   ├── __init__.py
│   └── voice_session.py               # Refactored: dependency injection
├── factory.py                         # Strategy factory
├── config.py                          # VoiceConfig (rewritten)
├── static/                            # Frontend (unchanged)
├── main.py                            # Refactored: uses factory
└── tests/                             # Tests adapted
```

## Changes Summary

| File | Change | Description |
|------|--------|-------------|
| `core/__init__.py` | New | Package init |
| `core/transcription_strategy.py` | New | ASR strategy interface |
| `core/correction_strategy.py` | New | Correction strategy interface |
| `engine/whisper_engine.py` | Minor | Implement `TranscriptionStrategy` |
| `engine/http_adapter.py` | Rewrite | Complete HTTP ASR implementation |
| `engine/websocket_adapter.py` | New | WebSocket streaming ASR |
| `correction/no_op_corrector.py` | New | NoOp correction |
| `correction/post_corrector.py` | Minor | Implement `CorrectionStrategy` |
| `session/voice_session.py` | Medium | Dependency injection |
| `factory.py` | New | Strategy factory |
| `config.py` | Rewrite | Read JSON config, remove .env |
| `main.py` | Medium | Use factory |
| `coloop-agent-setting.json` | Minor | Add `voice` field |
| `requirements.txt` | Minor | Remove `python-dotenv` |
| `.env` | Delete | No longer needed |
| `.env.example` | Delete | No longer needed |
| `tests/*` | Adapt | Mock strategies, test new implementations |
