from abc import ABC, abstractmethod
from typing import Union


class TranscriptionStrategy(ABC):
    """语音转文字策略接口"""

    @abstractmethod
    def transcribe(self, audio: Union[str, bytes], language: str = "zh") -> str:
        """将音频转为文本。

        Args:
            audio: 文件路径 (str) 或 PCM int16 音频字节 (bytes)。
                   传入文件路径时，引擎可利用 ffmpeg 处理 webm 等格式。
            language: 语言代码，默认中文。
        """
        ...

    @abstractmethod
    def get_name(self) -> str:
        """策略名称，用于日志和配置标识"""
        ...
