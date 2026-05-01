import pytest
import sys
from unittest.mock import Mock, patch, AsyncMock

# Inject faster-whisper mock before importing main
sys.modules["faster_whisper"] = Mock()
sys.modules["faster_whisper"].WhisperModel = Mock

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
            with patch.object(main, "get_engine", return_value=Mock()):
                with patch.object(main, "get_post_corrector", return_value=None):
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
