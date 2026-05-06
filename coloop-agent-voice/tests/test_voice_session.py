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
    def mock_transcription_strategy(self):
        strategy = Mock()
        strategy.transcribe.return_value = "test result"
        strategy.get_name.return_value = "mock_transcription"
        return strategy

    @pytest.fixture
    def mock_emit(self):
        return AsyncMock()

    @pytest.mark.asyncio
    async def test_feed_audio_triggers_transcribe(self, mock_transcription_strategy, mock_emit):
        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": True},
            transcription_strategy=mock_transcription_strategy,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * (16000 * 30 // 1000)
        with patch.object(session.vad, "process", return_value=audio):
            await session.feed_audio(audio)

        mock_transcription_strategy.transcribe.assert_called_once()
        mock_emit.assert_called()

    @pytest.mark.asyncio
    async def test_streaming_correction_disabled(self, mock_transcription_strategy, mock_emit):
        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": False},
            transcription_strategy=mock_transcription_strategy,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * (16000 * 30 // 1000)
        with patch.object(session.vad, "process", return_value=audio):
            await session.feed_audio(audio)

        mock_transcription_strategy.transcribe.assert_called_once()

    @pytest.mark.asyncio
    async def test_finalize_segment(self, mock_transcription_strategy, mock_emit):
        correction_strategy = AsyncMock()
        correction_strategy.correct.return_value = "corrected result"
        correction_strategy.get_name.return_value = "mock_correction"

        session = VoiceSession(
            config={"lang": "zh", "enable_streaming_correction": True, "enable_post_correction": True},
            transcription_strategy=mock_transcription_strategy,
            correction_strategy=correction_strategy,
            emit_callback=mock_emit,
        )

        audio = b"\x01\x00" * 100
        with patch.object(session.vad, "flush", return_value=audio):
            await session.finalize_segment()

        mock_emit.assert_any_call("segment_final", {"text": "test result", "segment_index": 0})
        mock_emit.assert_any_call("post_corrected", {"text": "corrected result", "original": "test result", "segment_index": 0})

    @pytest.mark.asyncio
    async def test_stop(self, mock_transcription_strategy, mock_emit):
        session = VoiceSession(
            config={"lang": "zh"},
            transcription_strategy=mock_transcription_strategy,
            emit_callback=mock_emit,
        )

        with patch.object(session, "finalize_segment", new_callable=AsyncMock):
            await session.stop()
            mock_emit.assert_called_with("complete", {"full_text": ""})
