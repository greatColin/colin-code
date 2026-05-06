import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import json
import websockets
from core.transcription_strategy import TranscriptionStrategy


class WebSocketTranscriptionStrategy(TranscriptionStrategy):
    """WebSocket-based streaming ASR transcription strategy"""

    def __init__(self, endpoint: str, api_key: str = "", timeout: float = 30.0):
        self.endpoint = endpoint
        self.api_key = api_key
        self.timeout = timeout

    async def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        """Send audio bytes via WebSocket and return transcription"""
        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        async with websockets.connect(
            self.endpoint,
            extra_headers=headers,
            open_timeout=self.timeout,
            close_timeout=self.timeout,
        ) as websocket:
            # Send audio data
            await websocket.send(audio_bytes)

            # Send end signal
            await websocket.send(json.dumps({"type": "end", "language": language}))

            # Collect transcription results
            transcription_parts = []
            async for message in websocket:
                data = json.loads(message)
                if data.get("type") == "transcript":
                    transcription_parts.append(data.get("text", ""))
                elif data.get("type") == "final":
                    break

            return " ".join(transcription_parts).strip()

    def get_name(self) -> str:
        return "websocket_transcription"
