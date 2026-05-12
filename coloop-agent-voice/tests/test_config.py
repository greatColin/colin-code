import json
import os
import tempfile
import pytest
from config import VoiceConfig


def test_defaults_when_no_file():
    config = VoiceConfig()
    assert config.get("host") == "0.0.0.0"
    assert config.get("port") == 8000
    assert config.get("language") == "zh"


def test_defaults_when_empty_config():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({}, f)
        config = VoiceConfig(setting_file=path)
        assert config.get("language") == "zh"
        assert config.get_transcription_strategy_name() == "local_whisper"
        assert config.get_correction_strategy_name() == "none"


def test_voice_config_override():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({"voice": {"language": "en", "port": 9000}}, f)
        config = VoiceConfig(setting_file=path)
        assert config.get("language") == "en"
        assert config.get("port") == 9000


def test_transcription_params():
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "setting.json")
        with open(path, "w") as f:
            json.dump({
                "voice": {
                    "transcription": {
                        "strategy": "http_api",
                        "strategies": {
                            "http_api": {"apiUrl": "https://example.com", "apiKey": "key123"}
                        }
                    }
                }
            }, f)
        config = VoiceConfig(setting_file=path)
        assert config.get_transcription_strategy_name() == "http_api"
        params = config.get_transcription_params("http_api")
        assert params["apiUrl"] == "https://example.com"


def test_env_var_expansion():
    os.environ["TEST_ASR_KEY"] = "expanded-value"
    try:
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "setting.json")
            with open(path, "w") as f:
                json.dump({
                    "voice": {
                        "transcription": {
                            "strategy": "http_api",
                            "strategies": {
                                "http_api": {"apiUrl": "https://example.com", "apiKey": "${TEST_ASR_KEY}"}
                            }
                        }
                    }
                }, f)
            config = VoiceConfig(setting_file=path)
            params = config.get_transcription_params("http_api")
            assert params["apiKey"] == "expanded-value"
    finally:
        del os.environ["TEST_ASR_KEY"]


def test_missing_file_uses_defaults():
    config = VoiceConfig(setting_file="/nonexistent/path.json")
    assert config.get("language") == "zh"


