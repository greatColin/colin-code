import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from typing import Optional
from config import VoiceConfig
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy


class VoiceFactory:
    """Factory for creating voice processing strategies based on configuration"""

    def __init__(self, config: VoiceConfig):
        self.config = config

    def create_transcription_strategy(self) -> TranscriptionStrategy:
        """Create transcription strategy based on configuration"""
        strategy_name = self.config.transcription_strategy

        if strategy_name == "local_whisper":
            from engine.whisper_engine import LocalWhisperStrategy
            return LocalWhisperStrategy(
                model=self.config.whisper_model,
                device=self.config.whisper_device,
                compute_type=self.config.whisper_compute_type,
                model_dir=str(self.config.whisper_model_dir),
            )
        elif strategy_name == "http_transcription":
            from engine.http_adapter import HttpTranscriptionStrategy
            return HttpTranscriptionStrategy(
                endpoint=self.config.transcription_endpoint,
                api_key=self.config.transcription_api_key,
            )
        elif strategy_name == "websocket_transcription":
            from engine.websocket_adapter import WebSocketTranscriptionStrategy
            return WebSocketTranscriptionStrategy(
                endpoint=self.config.transcription_endpoint,
                api_key=self.config.transcription_api_key,
            )
        else:
            raise ValueError(f"Unknown transcription strategy: {strategy_name}")

    def create_correction_strategy(self) -> CorrectionStrategy:
        """Create correction strategy based on configuration"""
        strategy_name = self.config.correction_strategy

        if strategy_name == "no_op":
            from correction.no_op_corrector import NoOpCorrectionStrategy
            return NoOpCorrectionStrategy()
        elif strategy_name == "llm_correction":
            from correction.post_corrector import LLMCorrectionStrategy
            correction_config = self.config.get_post_correction_config()
            if not correction_config:
                raise ValueError("LLM correction strategy requires post correction configuration")
            return LLMCorrectionStrategy(
                api_base=correction_config["api_base"],
                api_key=correction_config["api_key"],
                model=correction_config["model"],
            )
        else:
            raise ValueError(f"Unknown correction strategy: {strategy_name}")
