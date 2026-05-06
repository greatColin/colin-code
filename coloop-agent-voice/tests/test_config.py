import os
import json
import tempfile
from pathlib import Path
import pytest

from config import VoiceConfig


class TestVoiceConfigDefaults:
    def test_default_values_no_setting_file(self, monkeypatch, tmp_path):
        """Ensure default values are used when no setting file exists."""
        monkeypatch.chdir(tmp_path)
        cfg = VoiceConfig(setting_file="/nonexistent/path/setting.json")

        assert cfg.host == "0.0.0.0"
        assert cfg.port == 8000
        assert cfg.whisper_model == "base"
        assert cfg.whisper_device == "cpu"
        assert cfg.whisper_compute_type == "int8"
        assert cfg.whisper_model_dir == Path("./models")
        assert cfg.default_lang == "zh"
        assert cfg.enable_streaming_correction is True
        assert cfg.enable_post_correction is False
        assert cfg.post_correction_model == ""
        assert cfg.transcription_strategy == "local_whisper"
        assert cfg.correction_strategy == "no_op"


class TestVoiceConfigFromSettings:
    def test_load_voice_section(self, tmp_path):
        """Voice config should be loaded from the voice section in settings."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "voice": {
                        "host": "127.0.0.1",
                        "port": 9000,
                        "whisperModel": "large-v3",
                        "whisperDevice": "cuda",
                        "whisperComputeType": "float16",
                        "whisperModelDir": "/models",
                        "defaultLang": "en",
                        "enableStreamingCorrection": False,
                        "enablePostCorrection": True,
                        "postCorrectionModel": "minimax",
                        "transcriptionStrategy": "http_transcription",
                        "transcriptionEndpoint": "https://asr.example.com",
                    }
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))

        assert cfg.host == "127.0.0.1"
        assert cfg.port == 9000
        assert cfg.whisper_model == "large-v3"
        assert cfg.whisper_device == "cuda"
        assert cfg.whisper_compute_type == "float16"
        assert cfg.whisper_model_dir == Path("/models")
        assert cfg.default_lang == "en"
        assert cfg.enable_streaming_correction is False
        assert cfg.enable_post_correction is True
        assert cfg.post_correction_model == "minimax"
        assert cfg.transcription_strategy == "http_transcription"
        assert cfg.transcription_endpoint == "https://asr.example.com"

    def test_env_variable_expansion(self, tmp_path, monkeypatch):
        """Environment variables in settings should be expanded."""
        monkeypatch.setenv("TEST_API_KEY", "sk-test-123")
        monkeypatch.setenv("TEST_API_BASE", "https://api.test.com")

        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "test": {
                            "apiKey": "${TEST_API_KEY}",
                            "apiBase": "${TEST_API_BASE}",
                            "model": "test-model",
                        }
                    }
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        model_cfg = cfg.get_model_config("test")

        assert model_cfg["apiKey"] == "sk-test-123"
        assert model_cfg["apiBase"] == "https://api.test.com"

    def test_unexpanded_env_var_preserved(self, tmp_path):
        """Undefined env vars should be preserved as-is."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "models": {
                        "test": {
                            "apiKey": "${UNDEFINED_VAR}",
                        }
                    }
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        model_cfg = cfg.get_model_config("test")

        assert model_cfg["apiKey"] == "${UNDEFINED_VAR}"


class TestVoiceConfigModels:
    def test_get_model_config(self, tmp_path):
        """Models should be loaded from settings."""
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

        cfg = VoiceConfig(setting_file=str(setting_file))

        assert cfg.get_model_config("minimax")["apiBase"] == "https://api.minimaxi.com/v1"
        assert cfg.get_model_config("nonexistent") is None

    def test_missing_setting_file(self):
        """If setting file does not exist, models stay empty."""
        cfg = VoiceConfig(setting_file="/nonexistent/path/setting.json")
        assert cfg._models == {}


class TestVoiceConfigPostCorrection:
    def test_post_correction_config_resolution(self, tmp_path):
        """Post-correction config is resolved when enabled and model exists."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "voice": {
                        "enablePostCorrection": True,
                        "correctionModel": "minimax",
                    },
                    "models": {
                        "minimax": {
                            "apiKey": "sk-test",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                        }
                    },
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        pc_cfg = cfg.get_post_correction_config()

        assert pc_cfg is not None
        assert pc_cfg["api_base"] == "https://api.minimaxi.com/v1"
        assert pc_cfg["api_key"] == "sk-test"
        assert pc_cfg["model"] == "MiniMax-M2.7"

    def test_post_correction_disabled_returns_none(self, tmp_path):
        """When post-correction is disabled, get_post_correction_config returns None."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "voice": {
                        "enablePostCorrection": False,
                    },
                    "models": {
                        "minimax": {
                            "apiKey": "sk-test",
                            "apiBase": "https://api.minimaxi.com/v1",
                            "model": "MiniMax-M2.7",
                        }
                    },
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        assert cfg.get_post_correction_config() is None

    def test_post_correction_missing_model_returns_none(self, tmp_path):
        """When the configured post-correction model is missing, returns None."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "voice": {
                        "enablePostCorrection": True,
                        "correctionModel": "nonexistent",
                    },
                    "models": {},
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        assert cfg.get_post_correction_config() is None


class TestVoiceConfigTranscription:
    def test_get_transcription_config(self, tmp_path):
        """Transcription config should include all strategy-related settings."""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            json.dumps(
                {
                    "voice": {
                        "transcriptionStrategy": "http_transcription",
                        "transcriptionEndpoint": "https://asr.example.com",
                        "transcriptionApiKey": "sk-asr-key",
                        "whisperModel": "large-v3",
                        "whisperDevice": "cuda",
                        "whisperComputeType": "float16",
                        "whisperModelDir": "/models",
                        "defaultLang": "en",
                    }
                }
            ),
            encoding="utf-8",
        )

        cfg = VoiceConfig(setting_file=str(setting_file))
        tc = cfg.get_transcription_config()

        assert tc["strategy"] == "http_transcription"
        assert tc["endpoint"] == "https://asr.example.com"
        assert tc["api_key"] == "sk-asr-key"
        assert tc["model"] == "large-v3"
        assert tc["device"] == "cuda"
        assert tc["compute_type"] == "float16"
        assert tc["model_dir"] == "/models"
        assert tc["language"] == "en"
