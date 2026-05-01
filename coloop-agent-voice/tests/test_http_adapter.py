import pytest
from engine.http_adapter import HttpAdapter


class TestHttpAdapter:
    def test_init(self):
        adapter = HttpAdapter("https://api.example.com/asr")
        assert adapter.endpoint == "https://api.example.com/asr"

    @pytest.mark.asyncio
    async def test_transcribe_not_implemented(self):
        adapter = HttpAdapter("https://api.example.com/asr")
        with pytest.raises(NotImplementedError):
            await adapter.transcribe(b"audio", language="zh")
