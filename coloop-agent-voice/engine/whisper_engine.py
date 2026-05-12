import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from typing import Union

import numpy as np
from faster_whisper import WhisperModel
from core.transcription_strategy import TranscriptionStrategy


class LocalWhisperStrategy(TranscriptionStrategy):
    def __init__(self, model: str = "base", device: str = "cpu", compute_type: str = "int8", model_dir: str = "./models"):
        model_path = os.path.join(model_dir, model) if os.path.isdir(os.path.join(model_dir, model)) else model
        self.model = WhisperModel(model_path, device=device, compute_type=compute_type)

    def transcribe(self, audio: Union[str, bytes], language: str = "zh") -> str:
        """转写音频为文本。

        Args:
            audio: 文件路径 (str) 或 PCM int16 音频字节 (bytes)。
                   文件路径模式下 faster-whisper 通过 ffmpeg 处理 webm 等格式。
            language: 语言代码，默认中文。
        """
        if isinstance(audio, str):
            # 文件路径模式: faster-whisper 通过 ffmpeg 处理 webm 等格式
            source = audio
        else:
            # bytes 模式: 假设 PCM int16，转为 float32 归一化
            source = np.frombuffer(audio, dtype=np.int16).astype(np.float32) / 32768.0

        segments, _ = self.model.transcribe(
            source,
            language=language,
            beam_size=5,
            condition_on_previous_text=True,
        )
        return " ".join([seg.text.strip() for seg in segments]).strip()

    def get_name(self) -> str:
        return "local_whisper"


# Backward compatibility alias
WhisperEngine = LocalWhisperStrategy
