import pytest
import json
from unittest.mock import AsyncMock, patch, MagicMock
from engine.websocket_adapter import WebSocketTranscriptionStrategy


class TestWebSocketTranscriptionStrategy:
    def test_init(self):
        strategy = WebSocketTranscriptionStrategy("wss://api.example.com/asr")
        assert strategy.endpoint == "wss://api.example.com/asr"
        assert strategy.api_key == ""
        assert strategy.timeout == 30.0

    def test_init_with_api_key(self):
        strategy = WebSocketTranscriptionStrategy("wss://api.example.com/asr", api_key="test-key")
        assert strategy.api_key == "test-key"

    def test_get_name(self):
        strategy = WebSocketTranscriptionStrategy("wss://api.example.com/asr")
        assert strategy.get_name() == "websocket_transcription"

    @pytest.mark.asyncio
    async def test_transcribe_success(self):
        strategy = WebSocketTranscriptionStrategy("wss://api.example.com/asr", api_key="test-key")

        # Mock websocket responses
        responses = [
            json.dumps({"type": "transcript", "text": "Hello"}),
            json.dumps({"type": "transcript", "text": "world"}),
            json.dumps({"type": "final"}),
        ]

        mock_ws = AsyncMock()
        mock_ws.__aenter__ = AsyncMock(return_value=mock_ws)
        mock_ws.__aexit__ = AsyncMock(return_value=None)
        mock_ws.send = AsyncMock()
        mock_ws.__aiter__ = AsyncMock(return_value=iter(responses))

        with patch("websockets.connect", return_value=mock_ws):
            result = await strategy.transcribe(b"audio_data", language="en")
            assert result == "Hello world"

    @pytest.mark.asyncio
    async def test_transcribe_empty_response(self):
        strategy = WebSocketTranscriptionStrategy("wss://api.example.com/asr")

        responses = [
            json.dumps({"type": "final"}),
        ]

        mock_ws = AsyncMock()
        mock_ws.__aenter__ = AsyncMock(return_value=mock_ws)
        mock_ws.__aexit__ = AsyncMock(return_value=None)
        mock_ws.send = AsyncMock()
        mock_ws.__aiter__ = AsyncMock(return_value=iter(responses))

        with patch("websockets.connect", return_value=mock_ws):
            result = await strategy.transcribe(b"audio_data")
            assert result == ""
