import json
import pytest
import tempfile
import os
from factory import VoiceFactory
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from correction.no_op_corrector import NoOpCorrectionStrategy


def _write_config(tmp_dir, config_dict):
    path = os.path.join(tmp_dir, "test-setting.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(config_dict, f)
    return path


def test_create_correction_noop():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {"voice": {"correction": {"strategy": "none"}}})
        factory = VoiceFactory(setting_file=path)
        correction = factory.create_correction()
        assert isinstance(correction, CorrectionStrategy)
        assert correction.get_name() == "none"


def test_create_correction_default_is_noop():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        correction = factory.create_correction()
        assert correction.get_name() == "none"


def test_create_transcription_default_is_whisper():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        assert factory.config.get_transcription_strategy_name() == "local_whisper"


def test_config_defaults():
    with tempfile.TemporaryDirectory() as tmp:
        path = _write_config(tmp, {})
        factory = VoiceFactory(setting_file=path)
        assert factory.config.get("language") == "zh"
        assert factory.config.get("host") == "0.0.0.0"
        assert factory.config.get("port") == 8000
