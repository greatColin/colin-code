import asyncio
import pytest
import sys
from unittest.mock import Mock, patch, AsyncMock

# Inject faster-whisper mock before importing session
sys.modules["faster_whisper"] = Mock()
sys.modules["faster_whisper"].WhisperModel = Mock

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
