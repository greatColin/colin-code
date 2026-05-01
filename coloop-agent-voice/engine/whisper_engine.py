import numpy as np
from faster_whisper import WhisperModel


class WhisperEngine:
    def __init__(self, model_path: str, device: str = "cpu", compute_type: str = "int8"):
        self.model = WhisperModel(model_path, device=device, compute_type=compute_type)

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        audio_np = np.frombuffer(audio_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        segments, _ = self.model.transcribe(
            audio_np,
            language=language,
            beam_size=5,
            condition_on_previous_text=True,
        )
        return " ".join([seg.text.strip() for seg in segments]).strip()
