import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import json
import base64
import websocket
from core.transcription_strategy import TranscriptionStrategy


class WebSocketTranscriptionStrategy(TranscriptionStrategy):
    """通过 WebSocket 流式调用 ASR 服务的转写策略"""

    def __init__(self, ws_url: str, api_key: str = None):
        self.ws_url = ws_url
        self.api_key = api_key

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        ws = websocket.create_connection(self.ws_url, header=headers, timeout=30)
        try:
            payload = {
                "audio": base64.b64encode(audio_bytes).decode("utf-8"),
                "language": language,
                "is_last": True,
            }
            ws.send(json.dumps(payload))

            texts = []
            while True:
                raw = ws.recv()
                data = json.loads(raw)
                if "text" in data:
                    texts.append(data["text"])
                if data.get("is_final", True):
                    break
            return "".join(texts).strip()
        finally:
            ws.close()

    def get_name(self) -> str:
        return "websocket"
