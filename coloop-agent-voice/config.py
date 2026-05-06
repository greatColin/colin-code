import os
import re
import json
from pathlib import Path
from typing import Optional, Dict, Any


class VoiceConfig:
    def __init__(self, setting_file: Optional[str] = None):
        self._setting_file = setting_file or self._find_setting_file()
        self._settings: Dict[str, Any] = {}
        self._voice_config: Dict[str, Any] = {}
        self._models: Dict[str, Any] = {}

        if self._setting_file and Path(self._setting_file).exists():
            self._load_settings()

        # Apply voice configuration with defaults
        voice = self._voice_config
        self.host = voice.get("host", "0.0.0.0")
        self.port = int(voice.get("port", 8000))
        self.whisper_model = voice.get("whisperModel", "base")
        self.whisper_device = voice.get("whisperDevice", "cpu")
        self.whisper_compute_type = voice.get("whisperComputeType", "int8")
        self.whisper_model_dir = Path(voice.get("whisperModelDir", "./models"))
        self.default_lang = voice.get("defaultLang", "zh")
        self.enable_streaming_correction = voice.get("enableStreamingCorrection", True)
        self.enable_post_correction = voice.get("enablePostCorrection", False)
        self.post_correction_model = voice.get("postCorrectionModel", "")

        # Transcription strategy configuration
        self.transcription_strategy = voice.get("transcriptionStrategy", "local_whisper")
        self.transcription_endpoint = voice.get("transcriptionEndpoint", "")
        self.transcription_api_key = voice.get("transcriptionApiKey", "")

        # Correction strategy configuration
        self.correction_strategy = voice.get("correctionStrategy", "no_op")
        self.correction_model = voice.get("correctionModel", "")

    def _find_setting_file(self) -> Optional[str]:
        """Find the setting file in common locations"""
        candidates = [
            Path("coloop-agent-setting.json"),
            Path("../coloop-agent-setting.json"),
            Path("coloop-agent-core/src/main/resources/coloop-agent-setting.json"),
            Path.home() / ".coloop" / "coloop-agent-setting.json",
        ]
        for candidate in candidates:
            if candidate.exists():
                return str(candidate)
        return None

    def _load_settings(self):
        """Load settings from JSON file with environment variable expansion"""
        with open(self._setting_file, "r", encoding="utf-8") as f:
            content = f.read()
            # Expand environment variables like ${VAR_NAME}
            content = re.sub(
                r"\$\{(\w+)\}",
                lambda m: os.environ.get(m.group(1), m.group(0)),
                content,
            )
            self._settings = json.loads(content)

        self._models = self._settings.get("models", {})
        self._voice_config = self._settings.get("voice", {})

    def get_model_config(self, name: str) -> Optional[Dict[str, str]]:
        return self._models.get(name)

    def get_post_correction_config(self) -> Optional[Dict[str, str]]:
        if not self.enable_post_correction:
            return None

        model_name = self.correction_model or self.post_correction_model
        if not model_name:
            return None

        cfg = self._models.get(model_name)
        if cfg:
            return {
                "api_base": cfg.get("apiBase", ""),
                "api_key": cfg.get("apiKey", ""),
                "model": cfg.get("model", ""),
            }
        return None

    def get_transcription_config(self) -> Dict[str, Any]:
        """Get transcription strategy configuration"""
        return {
            "strategy": self.transcription_strategy,
            "endpoint": self.transcription_endpoint,
            "api_key": self.transcription_api_key,
            "model": self.whisper_model,
            "device": self.whisper_device,
            "compute_type": self.whisper_compute_type,
            "model_dir": str(self.whisper_model_dir),
            "language": self.default_lang,
        }
