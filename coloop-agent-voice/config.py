import json
import re
import os
from pathlib import Path
from typing import Any, Dict, Optional


class VoiceConfig:
    """Voice module configuration."""

    DEFAULTS = {
        "host": "0.0.0.0",
        "port": 8000,
        "language": "zh",
    }

    def __init__(self, setting_file: str = None):
        self._raw: Dict[str, Any] = {}
        self._models: Dict[str, Any] = {}
        if setting_file:
            self._load(setting_file)
        self._voice = self._raw.get("voice", {})

    def _load(self, path: str):
        p = Path(path)
        if not p.exists():
            print(f"[VoiceConfig] setting file not found: {path}, using defaults")
            return
        with open(p, "r", encoding="utf-8") as f:
            raw_text = f.read()
        raw_text = self._strip_json_comments(raw_text)
        raw_text = self._expand_env_vars(raw_text)
        self._raw = json.loads(raw_text)
        self._models = self._raw.get("models", {})

    @staticmethod
    def _strip_json_comments(text: str) -> str:
        # Strip // line and /* */ block comments while preserving string contents.
        pattern = re.compile(
            r'("(?:\\.|[^"\\])*")|(//[^\n]*|/\*[\s\S]*?\*/)',
            re.MULTILINE,
        )
        return pattern.sub(lambda m: m.group(1) if m.group(1) else "", text)

    @staticmethod
    def _expand_env_vars(text: str) -> str:
        def replacer(match):
            var_name = match.group(1)
            return os.environ.get(var_name, match.group(0))
        return re.sub(r"\$\{(\w+)\}", replacer, text)

    def get(self, key: str, default: Any = None) -> Any:
        fallback = default if default is not None else self.DEFAULTS.get(key)
        return self._voice.get(key, fallback)

    def get_transcription_strategy_name(self) -> str:
        return self._voice.get("transcription", {}).get("strategy", "local_whisper")

    def get_transcription_params(self, strategy_name: str) -> dict:
        return self._voice.get("transcription", {}).get("strategies", {}).get(strategy_name, {})

    def get_correction_strategy_name(self) -> str:
        return self._voice.get("correction", {}).get("strategy", "none")

    def get_correction_params(self, strategy_name: str) -> dict:
        return self._voice.get("correction", {}).get("strategies", {}).get(strategy_name, {})

    def get_model_config(self, name: str) -> Optional[Dict[str, Any]]:
        return self._models.get(name)

    @property
    def host(self) -> str:
        return self.get("host")

    @property
    def port(self) -> int:
        return self.get("port")
