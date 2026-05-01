import os
import json
import tempfile
from pathlib import Path
import pytest

from config import VoiceConfig


class TestVoiceConfigDefaults:
    def test_default_values(self, monkeypatch):
        """Ensure default values are used when env vars are not set."""
        monkeypatch.delenv("HOST", raising=False)
        monkeypatch.delenv("PORT", raising=False)
        monkeypatch.delenv("WHISPER_MODEL", raising=False)
        monkeypatch.delenv("WHISPER_DEVICE", raising=False)
        monkeypatch.delenv("WHISPER_COMPUTE_TYPE", raising=False)
        monkeypatch.delenv("WHISPER_MODEL_DIR", raising=False)
        monkeypatch.delenv("DEFAULT_LANG", raising=False)
        monkeypatch.delenv("ENABLE_STREAMING_CORRECTION", raising=False)
        monkeypatch.delenv("ENABLE_POST_CORRECTION", raising=False)
        monkeypatch.delenv("SETTING_FILE", raising=False)
        monkeypatch.delenv("POST_CORRECTION_MODEL", raising=False)

        cfg = VoiceConfig()

        assert cfg.host == "0.0.0.0"
        assert cfg.port == 8000
        assert cfg.whisper_model == "base"
        assert cfg.whisper_device == "cpu"
        assert cfg.whisper_compute_type == "int8"
        assert cfg.whisper_model_dir == Path("./models")
        assert cfg.default_lang == "zh"
        assert cfg.enable_streaming_correction is True
        assert cfg.enable_post_correction is False
        assert cfg.setting_file == ""
        assert cfg.post_correction_model == ""
        assert cfg._models == {}


class TestVoiceConfigModelLoading:
    def test_load_models_from_setting_file(self, monkeypatch, tmp_path):
        """Models should be loaded from the JSON setting file."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "minimax": {
                            "apiKey": "${COLIN_CODE_MINIMAX_API_KEY}",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                            "maxContextSize": "200k",
                        },
                        "openai": {
                            "apiKey": "${COLIN_CODE_OPENAI_API_KEY}",
                            "apiBase": "${COLIN_CODE_OPENAI_API_BASE}",
                            "model": "${COLIN_CODE_OPENAI_MODEL}",
                        },
                    }
                }
            ),
            encoding="utf-8",
        )

        monkeypatch.setenv("SETTING_FILE", str(setting_file))
        cfg = VoiceConfig()

        assert "minimax" in cfg._models
        assert "openai" in cfg._models
        assert cfg.get_model_config("minimax")["apiBase"] == "https://api.minimaxi.com/v1"
        assert cfg.get_model_config("openai")["model"] == "${COLIN_CODE_OPENAI_MODEL}"
        assert cfg.get_model_config("nonexistent") is None

    def test_missing_setting_file_ignored(self, monkeypatch):
        """If setting file does not exist, _models stays empty."""
        monkeypatch.setenv("SETTING_FILE", "/nonexistent/path/setting.json")
        cfg = VoiceConfig()
        assert cfg._models == {}


class TestVoiceConfigPostCorrection:
    def test_post_correction_config_resolution(self, monkeypatch, tmp_path):
        """Post-correction config is resolved when enabled and model exists."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "minimax": {
                            "apiKey": "sk-test",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                        }
                    }
                }
            ),
            encoding="utf-8",
        )

        monkeypatch.setenv("SETTING_FILE", str(setting_file))
        monkeypatch.setenv("ENABLE_POST_CORRECTION", "true")
        monkeypatch.setenv("POST_CORRECTION_MODEL", "minimax")

        cfg = VoiceConfig()
        pc_cfg = cfg.get_post_correction_config()

        assert pc_cfg is not None
        assert pc_cfg["api_base"] == "https://api.minimaxi.com/v1"
        assert pc_cfg["api_key"] == "sk-test"
        assert pc_cfg["model"] == "MiniMax-M2.7"

    def test_post_correction_disabled_returns_none(self, monkeypatch, tmp_path):
        """When post-correction is disabled, get_post_correction_config returns None."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "minimax": {
                            "apiKey": "sk-test",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                        }
                    }
                }
            ),
            encoding="utf-8",
        )

        monkeypatch.setenv("SETTING_FILE", str(setting_file))
        monkeypatch.setenv("ENABLE_POST_CORRECTION", "false")
        monkeypatch.setenv("POST_CORRECTION_MODEL", "minimax")

        cfg = VoiceConfig()
        assert cfg.get_post_correction_config() is None

    def test_post_correction_missing_model_returns_none(self, monkeypatch, tmp_path):
        """When the configured post-correction model is missing, returns None."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(json.dumps({"models": {}}), encoding="utf-8")

        monkeypatch.setenv("SETTING_FILE", str(setting_file))
        monkeypatch.setenv("ENABLE_POST_CORRECTION", "true")
        monkeypatch.setenv("POST_CORRECTION_MODEL", "minimax")

        cfg = VoiceConfig()
        assert cfg.get_post_correction_config() is None

    def test_post_correction_empty_model_returns_none(self, monkeypatch, tmp_path):
        """When post-correction model name is empty, returns None."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "minimax": {
                            "apiKey": "sk-test",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                        }
                    }
                }
            ),
            encoding="utf-8",
        )

        monkeypatch.setenv("SETTING_FILE", str(setting_file))
        monkeypatch.setenv("ENABLE_POST_CORRECTION", "true")
        monkeypatch.setenv("POST_CORRECTION_MODEL", "")

        cfg = VoiceConfig()
        assert cfg.get_post_correction_config() is None
