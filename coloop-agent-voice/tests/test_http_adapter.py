import pytest
from unittest.mock import patch, MagicMock
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
