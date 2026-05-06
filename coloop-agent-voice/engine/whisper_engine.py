import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np
from faster_whisper import WhisperModel
from core.transcription_strategy import TranscriptionStrategy


class LocalWhisperStrategy(TranscriptionStrategy):
    def __init__(self, model: str = "base", device: str = "cpu", compute_type: str = "int8", model_dir: str = "./models"):
        model_path = os.path.join(model_dir, model) if os.path.isdir(os.path.join(model_dir, model)) else model
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

    def get_name(self) -> str:
        return "local_whisper"


# Backward compatibility alias
WhisperEngine = LocalWhisperStrategy
