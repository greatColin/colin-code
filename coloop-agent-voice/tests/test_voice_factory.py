import pytest
from unittest.mock import patch, MagicMock
from config import VoiceConfig
from voice_factory import VoiceFactory
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy


class TestVoiceFactoryTranscription:
    def test_create_local_whisper_strategy(self, tmp_path):
        """Should create LocalWhisperStrategy for local_whisper config"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text('{"voice": {"transcriptionStrategy": "local_whisper"}}', encoding="utf-8")

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with patch("engine.whisper_engine.LocalWhisperStrategy") as mock_strategy:
            mock_instance = MagicMock(spec=TranscriptionStrategy)
            mock_strategy.return_value = mock_instance

            strategy = factory.create_transcription_strategy()

            mock_strategy.assert_called_once_with(
                model="base",
                device="cpu",
                compute_type="int8",
                model_dir="./models",
            )
            assert strategy == mock_instance

    def test_create_http_transcription_strategy(self, tmp_path):
        """Should create HttpTranscriptionStrategy for http_transcription config"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            '{"voice": {"transcriptionStrategy": "http_transcription", "transcriptionEndpoint": "https://asr.example.com", "transcriptionApiKey": "sk-test"}}',
            encoding="utf-8",
        )

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with patch("engine.http_adapter.HttpTranscriptionStrategy") as mock_strategy:
            mock_instance = MagicMock(spec=TranscriptionStrategy)
            mock_strategy.return_value = mock_instance

            strategy = factory.create_transcription_strategy()

            mock_strategy.assert_called_once_with(
                endpoint="https://asr.example.com",
                api_key="sk-test",
            )
            assert strategy == mock_instance

    def test_create_websocket_transcription_strategy(self, tmp_path):
        """Should create WebSocketTranscriptionStrategy for websocket_transcription config"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            '{"voice": {"transcriptionStrategy": "websocket_transcription", "transcriptionEndpoint": "wss://asr.example.com", "transcriptionApiKey": "sk-test"}}',
            encoding="utf-8",
        )

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with patch("engine.websocket_adapter.WebSocketTranscriptionStrategy") as mock_strategy:
            mock_instance = MagicMock(spec=TranscriptionStrategy)
            mock_strategy.return_value = mock_instance

            strategy = factory.create_transcription_strategy()

            mock_strategy.assert_called_once_with(
                endpoint="wss://asr.example.com",
                api_key="sk-test",
            )
            assert strategy == mock_instance

    def test_create_unknown_transcription_strategy_raises(self, tmp_path):
        """Should raise ValueError for unknown transcription strategy"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text('{"voice": {"transcriptionStrategy": "unknown"}}', encoding="utf-8")

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with pytest.raises(ValueError, match="Unknown transcription strategy"):
            factory.create_transcription_strategy()


class TestVoiceFactoryCorrection:
    def test_create_no_op_correction_strategy(self, tmp_path):
        """Should create NoOpCorrectionStrategy for no_op config"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text('{"voice": {"correctionStrategy": "no_op"}}', encoding="utf-8")

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with patch("correction.no_op_corrector.NoOpCorrectionStrategy") as mock_strategy:
            mock_instance = MagicMock(spec=CorrectionStrategy)
            mock_strategy.return_value = mock_instance

            strategy = factory.create_correction_strategy()

            mock_strategy.assert_called_once()
            assert strategy == mock_instance

    def test_create_llm_correction_strategy(self, tmp_path):
        """Should create LLMCorrectionStrategy for llm_correction config"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text(
            '{"voice": {"correctionStrategy": "llm_correction", "enablePostCorrection": true, "correctionModel": "minimax"}, "models": {"minimax": {"apiKey": "sk-test", "apiBase": "https://api.minimaxi.com/v1", "model": "MiniMax-M2.7"}}}',
            encoding="utf-8",
        )

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with patch("correction.post_corrector.LLMCorrectionStrategy") as mock_strategy:
            mock_instance = MagicMock(spec=CorrectionStrategy)
            mock_strategy.return_value = mock_instance

            strategy = factory.create_correction_strategy()

            mock_strategy.assert_called_once_with(
                api_base="https://api.minimaxi.com/v1",
                api_key="sk-test",
                model="MiniMax-M2.7",
            )
            assert strategy == mock_instance

    def test_create_llm_correction_strategy_without_config_raises(self, tmp_path):
        """Should raise ValueError when llm_correction config is missing"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text('{"voice": {"correctionStrategy": "llm_correction"}}', encoding="utf-8")

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with pytest.raises(ValueError, match="LLM correction strategy requires post correction configuration"):
            factory.create_correction_strategy()

    def test_create_unknown_correction_strategy_raises(self, tmp_path):
        """Should raise ValueError for unknown correction strategy"""
        setting_file = tmp_path / "coloop-agent-setting.json"
        setting_file.write_text('{"voice": {"correctionStrategy": "unknown"}}', encoding="utf-8")

        config = VoiceConfig(setting_file=str(setting_file))
        factory = VoiceFactory(config)

        with pytest.raises(ValueError, match="Unknown correction strategy"):
            factory.create_correction_strategy()
