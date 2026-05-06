import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import httpx
import base64
from core.transcription_strategy import TranscriptionStrategy


class HttpTranscriptionStrategy(TranscriptionStrategy):
    """通过 HTTP 调用外部 ASR API 的转写策略"""

    def __init__(self, api_url: str, api_key: str, model: str = None):
        self.api_url = api_url.rstrip("/")
        self.api_key = api_key
        self.model = model

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        audio_b64 = base64.b64encode(audio_bytes).decode("utf-8")
        payload = {
            "audio": audio_b64,
            "language": language,
        }
        if self.model:
            payload["model"] = self.model

        with httpx.Client(timeout=30.0) as client:
            response = client.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
            return data.get("text", "").strip()

    def get_name(self) -> str:
        return "http_api"


# Backward compatibility alias
HttpAdapter = HttpTranscriptionStrategy
