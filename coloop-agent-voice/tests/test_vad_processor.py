import pytest
from unittest.mock import Mock, patch

from audio.vad_processor import VADProcessor


class TestVADProcessor:
    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_voice_segment_detected(self, mock_vad_class):
        mock_vad = Mock()
        # Simulate: 10 frames silence, 10 frames speech, 10 frames silence
        # With ring_buffer_maxlen=10, need 10 voiced frames to trigger start
        # and 10 unvoiced frames to trigger end
        mock_vad.is_speech.side_effect = (
            [False] * 10 + [True] * 10 + [False] * 10
        )
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor(aggressiveness=3, sample_rate=16000, frame_duration_ms=30, ring_buffer_maxlen=10)

        silence = b"\x00" * (16000 * 2 * 30 // 1000)  # 30ms of 16bit silence
        speech = b"\x01\x00" * (16000 * 30 // 1000)

        # Feed silence (should not trigger)
        for _ in range(10):
            result = processor.process(silence)
            assert result is None

        # Feed speech (should trigger start after ring buffer fills)
        for _ in range(10):
            result = processor.process(speech)
            assert result is None  # start trigger doesn't return

        # Feed silence again (should trigger end after ring buffer fills)
        for i in range(10):
            result = processor.process(silence)
            if i >= 9:
                assert result is not None
                assert isinstance(result, bytes)

    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_flush_returns_remaining(self, mock_vad_class):
        mock_vad = Mock()
        mock_vad.is_speech.return_value = True
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor(ring_buffer_maxlen=1)
        speech = b"\x01\x00" * (16000 * 30 // 1000)
        processor.process(speech)

        result = processor.flush()
        assert result is not None
        assert isinstance(result, bytes)

    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_flush_when_not_triggered_returns_none(self, mock_vad_class):
        mock_vad = Mock()
        mock_vad.is_speech.return_value = False
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor()
        silence = b"\x00" * (16000 * 2 * 30 // 1000)
        processor.process(silence)

        result = processor.flush()
        assert result is None

    @patch("audio.vad_processor.webrtcvad.Vad")
    def test_partial_frame_buffered(self, mock_vad_class):
        mock_vad = Mock()
        mock_vad.is_speech.return_value = False
        mock_vad_class.return_value = mock_vad

        processor = VADProcessor(frame_duration_ms=30)
        frame_bytes = 16000 * 2 * 30 // 1000  # 960 bytes for 30ms @ 16kHz 16bit

        # Send a partial frame (less than one VAD frame)
        partial = b"\x00" * (frame_bytes // 2)
        result = processor.process(partial)
        assert result is None
        # The partial bytes should be buffered in _partial, not _buffer
        assert len(processor._partial) == len(partial)
        assert len(processor._buffer) == 0
