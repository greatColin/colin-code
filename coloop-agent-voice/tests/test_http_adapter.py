import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from engine.http_adapter import HttpTranscriptionStrategy


class TestHttpTranscriptionStrategy:
    def test_init(self):
        strategy = HttpTranscriptionStrategy("https://api.example.com/asr")
        assert strategy.endpoint == "https://api.example.com/asr"
        assert strategy.api_key == ""
        assert strategy.timeout == 30.0

    def test_init_with_api_key(self):
        strategy = HttpTranscriptionStrategy("https://api.example.com/asr", api_key="test-key")
        assert strategy.api_key == "test-key"

    def test_get_name(self):
        strategy = HttpTranscriptionStrategy("https://api.example.com/asr")
        assert strategy.get_name() == "http_transcription"

    @pytest.mark.asyncio
    async def test_transcribe_success(self):
        strategy = HttpTranscriptionStrategy("https://api.example.com/asr", api_key="test-key")

        mock_response = MagicMock()
        mock_response.json.return_value = {"text": "Hello world"}
        mock_response.raise_for_status = MagicMock()

        with patch("httpx.AsyncClient") as mock_client:
            mock_client.return_value.__aenter__ = AsyncMock(return_value=mock_client.return_value)
            mock_client.return_value.__aexit__ = AsyncMock(return_value=None)
            mock_client.return_value.post = AsyncMock(return_value=mock_response)

            result = await strategy.transcribe(b"audio_data", language="en")
            assert result == "Hello world"

    @pytest.mark.asyncio
    async def test_transcribe_empty_response(self):
        strategy = HttpTranscriptionStrategy("https://api.example.com/asr")

        mock_response = MagicMock()
        mock_response.json.return_value = {"text": ""}
        mock_response.raise_for_status = MagicMock()

        with patch("httpx.AsyncClient") as mock_client:
            mock_client.return_value.__aenter__ = AsyncMock(return_value=mock_client.return_value)
            mock_client.return_value.__aexit__ = AsyncMock(return_value=None)
            mock_client.return_value.post = AsyncMock(return_value=mock_response)

            result = await strategy.transcribe(b"audio_data")
            assert result == ""

    def test_backward_compatibility_alias(self):
        from engine.http_adapter import HttpAdapter
        assert HttpAdapter is HttpTranscriptionStrategy
