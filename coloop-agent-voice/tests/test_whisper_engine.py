import sys
from unittest.mock import Mock, patch

# Inject faster-whisper mock before importing engine
sys.modules["faster_whisper"] = Mock()
sys.modules["faster_whisper"].WhisperModel = Mock

import numpy as np
from engine.whisper_engine import WhisperEngine


class TestWhisperEngine:
    def test_transcribe(self):
        mock_segment = Mock()
        mock_segment.text = "  hello world  "
        mock_model = Mock()
        mock_model.transcribe.return_value = ([mock_segment], None)

        with patch("engine.whisper_engine.WhisperModel", return_value=mock_model):
            engine = WhisperEngine("base", device="cpu", compute_type="int8")
            audio_bytes = np.zeros(16000, dtype=np.int16).tobytes()
            result = engine.transcribe(audio_bytes, language="en")

        assert result == "hello world"
        mock_model.transcribe.assert_called_once()
        call_kwargs = mock_model.transcribe.call_args[1]
        assert call_kwargs["language"] == "en"
        assert call_kwargs["beam_size"] == 5
        assert call_kwargs["condition_on_previous_text"] is True

    def test_transcribe_multiple_segments(self):
        seg1 = Mock()
        seg1.text = "first"
        seg2 = Mock()
        seg2.text = "second"
        mock_model = Mock()
        mock_model.transcribe.return_value = ([seg1, seg2], None)

        with patch("engine.whisper_engine.WhisperModel", return_value=mock_model):
            engine = WhisperEngine("base")
            audio_bytes = np.zeros(16000, dtype=np.int16).tobytes()
            result = engine.transcribe(audio_bytes, language="zh")

        assert result == "first second"
