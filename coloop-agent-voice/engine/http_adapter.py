import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import httpx
from core.transcription_strategy import TranscriptionStrategy


class HttpTranscriptionStrategy(TranscriptionStrategy):
    """HTTP-based ASR transcription strategy using a remote API endpoint"""

    def __init__(self, endpoint: str, api_key: str = "", timeout: float = 30.0):
        self.endpoint = endpoint.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout

    async def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        """Send audio bytes to remote ASR endpoint and return transcription"""
        headers = {"Content-Type": "audio/raw"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.endpoint}?language={language}",
                content=audio_bytes,
                headers=headers,
            )
            response.raise_for_status()
            data = response.json()
            return data.get("text", "").strip()

    def get_name(self) -> str:
        return "http_transcription"


# Backward compatibility alias
HttpAdapter = HttpTranscriptionStrategy
