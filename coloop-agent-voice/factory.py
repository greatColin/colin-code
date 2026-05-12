from config import VoiceConfig
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from correction.no_op_corrector import NoOpCorrectionStrategy


class VoiceFactory:
    """根据配置创建策略实例并组装 VoiceSession"""

    def __init__(self, setting_file: str = None):
        self.config = VoiceConfig(setting_file)

    def create_transcription(self) -> TranscriptionStrategy:
        name = self.config.get_transcription_strategy_name()
        params = self.config.get_transcription_params(name)

        if name == "local_whisper":
            from engine.whisper_engine import LocalWhisperStrategy
            return LocalWhisperStrategy(
                model=params.get("model", "base"),
                device=params.get("device", "cpu"),
                compute_type=params.get("computeType", "int8"),
                model_dir=params.get("modelDir", "./models"),
            )
        else:
            raise ValueError(f"Unknown transcription strategy: {name}")

    def create_correction(self) -> CorrectionStrategy:
        name = self.config.get_correction_strategy_name()
        params = self.config.get_correction_params(name)

        if name == "llm":
            from correction.post_corrector import LLMCorrectionStrategy
            model_name = params.get("model", "minimax")
            model_cfg = self.config.get_model_config(model_name)
            if model_cfg:
                return LLMCorrectionStrategy(
                    api_base=model_cfg.get("apiBase", ""),
                    api_key=model_cfg.get("apiKey", ""),
                    model=model_cfg.get("model", ""),
                )
            else:
                raise ValueError(f"Model '{model_name}' not found in models config")
        elif name == "none":
            return NoOpCorrectionStrategy()
        else:
            raise ValueError(f"Unknown correction strategy: {name}")
