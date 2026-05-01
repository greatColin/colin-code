import os
import json
from pathlib import Path
from dotenv import load_dotenv
from typing import Optional, Dict, Any

load_dotenv()


class VoiceConfig:
    def __init__(self):
        self.host = os.getenv("HOST", "0.0.0.0")
        self.port = int(os.getenv("PORT", "8000"))
        self.whisper_model = os.getenv("WHISPER_MODEL", "base")
        self.whisper_device = os.getenv("WHISPER_DEVICE", "cpu")
        self.whisper_compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.whisper_model_dir = Path(os.getenv("WHISPER_MODEL_DIR", "./models"))
        self.default_lang = os.getenv("DEFAULT_LANG", "zh")
        self.enable_streaming_correction = os.getenv("ENABLE_STREAMING_CORRECTION", "true").lower() == "true"
        self.enable_post_correction = os.getenv("ENABLE_POST_CORRECTION", "false").lower() == "true"
        self.setting_file = os.getenv("SETTING_FILE", "")
        self.post_correction_model = os.getenv("POST_CORRECTION_MODEL", "")

        self._models: Dict[str, Any] = {}
        self._load_models()

    def _load_models(self):
        if self.setting_file and Path(self.setting_file).exists():
            with open(self.setting_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                self._models = data.get("models", {})

    def get_model_config(self, name: str) -> Optional[Dict[str, str]]:
        return self._models.get(name)

    def get_post_correction_config(self) -> Optional[Dict[str, str]]:
        if not self.enable_post_correction or not self.post_correction_model:
            return None
        cfg = self._models.get(self.post_correction_model)
        if cfg:
            return {
                "api_base": cfg.get("apiBase", ""),
                "api_key": cfg.get("apiKey", ""),
                "model": cfg.get("model", ""),
            }
        return None
