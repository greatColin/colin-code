import pytest
from engine.websocket_adapter import WebSocketTranscriptionStrategy


def test_get_name():
    s = WebSocketTranscriptionStrategy(ws_url="wss://asr.example.com/ws")
    assert s.get_name() == "websocket"


def test_init_with_api_key():
    s = WebSocketTranscriptionStrategy(ws_url="wss://asr.example.com/ws", api_key="test-key")
    assert s.ws_url == "wss://asr.example.com/ws"
    assert s.api_key == "test-key"
