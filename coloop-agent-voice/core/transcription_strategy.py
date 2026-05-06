from abc import ABC, abstractmethod


class TranscriptionStrategy(ABC):
    """语音转文字策略接口"""

    @abstractmethod
    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        """将 PCM int16 音频字节转为文本"""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """策略名称，用于日志和配置标识"""
        ...
