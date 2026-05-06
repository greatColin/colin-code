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

    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    assert len(transcription.calls) == 1
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
