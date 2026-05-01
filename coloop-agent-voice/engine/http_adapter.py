class HttpAdapter:
    def __init__(self, endpoint: str):
        self.endpoint = endpoint

    async def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        raise NotImplementedError("HTTP adapter not yet implemented")
