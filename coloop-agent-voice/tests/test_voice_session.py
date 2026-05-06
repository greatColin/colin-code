import pytest
import asyncio
from unittest.mock import MagicMock, AsyncMock
from session.voice_session import VoiceSession
from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy


class MockTranscription(TranscriptionStrategy):
    def __init__(self, result="hello"):
        self.result = result
        self.calls = []

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        self.calls.append(("transcribe", audio_bytes, language))
        return self.result

    def get_name(self) -> str:
        return "mock"


class MockCorrection(CorrectionStrategy):
    def __init__(self, result="corrected"):
        self.result = result

    async def correct(self, text: str) -> str:
        return self.result

    def get_name(self) -> str:
        return "mock"


@pytest.mark.asyncio
async def test_session_uses_injected_strategies():
    transcription = MockTranscription(result="你好")
    correction = MockCorrection(result="你好。")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        language="zh",
        enable_streaming_correction=False,
    )

    await session._finalize_segment_audio(b"\x00\x00" * 16000)

    assert len(transcription.calls) == 1
    events = [e[0] for e in emitted]
    assert "segment_final" in events
    assert "post_corrected" in events


@pytest.mark.asyncio
async def test_session_stop_emits_complete():
    transcription = MockTranscription(result="")
    correction = MockCorrection()
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
    )
    session.full_text = "hello world "
    await session.stop()

    assert ("complete", {"full_text": "hello world"}) in emitted


# --- Task 2: recognition_mode parameter ---

@pytest.mark.asyncio
async def test_session_default_recognition_mode():
    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
    )
    assert session.recognition_mode == "realtime"


@pytest.mark.asyncio
async def test_session_recognition_mode_param():
    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
        recognition_mode="final_only",
    )
    assert session.recognition_mode == "final_only"


# --- Task 3: realtime_final mode ---

class CountingTranscription(TranscriptionStrategy):
    """Returns incrementing results for each call."""
    def __init__(self):
        self.call_count = 0

    def transcribe(self, audio_bytes: bytes, language: str = "zh") -> str:
        self.call_count += 1
        return f"result_{self.call_count}"

    def get_name(self) -> str:
        return "counting"


@pytest.mark.asyncio
async def test_realtime_final_stores_raw_segments():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection(result="hello")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime_final",
    )
    audio1 = b"\x00\x00" * 16000
    audio2 = b"\x01\x01" * 16000

    await session._finalize_segment_audio(audio1)
    await session._finalize_segment_audio(audio2)

    assert len(session._raw_segments) == 2
    assert session._raw_segments[0] == audio1
    assert session._raw_segments[1] == audio2


@pytest.mark.asyncio
async def test_realtime_final_stop_retranscribes():
    transcription = CountingTranscription()
    correction = MockCorrection(result="final_corrected")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="realtime_final",
    )
    audio = b"\x00\x00" * 16000  # 1 second, small enough to not chunk

    await session._finalize_segment_audio(audio)
    await session._finalize_segment_audio(audio)

    # Clear events from finalize calls
    emitted.clear()

    await session.stop()

    # Should have a complete event with final_text
    complete_events = [e for e in emitted if e[0] == "complete"]
    assert len(complete_events) == 1
    assert "final_text" in complete_events[0][1]


# --- Task 4: final_only mode ---

@pytest.mark.asyncio
async def test_final_only_no_segment_events_during_recording():
    transcription = MockTranscription(result="hello")
    correction = MockCorrection(result="hello")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
    )
    audio = b"\x00\x00" * 16000

    await session._finalize_segment_audio(audio)
    await session._finalize_segment_audio(audio)

    events = [e[0] for e in emitted]
    assert "segment_final" not in events
    assert "post_corrected" not in events


@pytest.mark.asyncio
async def test_final_only_stop_transcribes_all():
    transcription = CountingTranscription()
    correction = MockCorrection(result="final_text")
    emitted = []

    async def emit(event, payload):
        emitted.append((event, payload))

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        recognition_mode="final_only",
    )
    audio = b"\x00\x00" * 16000

    await session._finalize_segment_audio(audio)
    await session._finalize_segment_audio(audio)
    await session._finalize_segment_audio(audio)

    assert len(session._raw_segments) == 3

    emitted.clear()
    await session.stop()

    complete_events = [e for e in emitted if e[0] == "complete"]
    assert len(complete_events) == 1
    # transcribe was called during stop for the long audio
    assert transcription.call_count >= 1


# --- Task 5: Chunking tests ---

@pytest.mark.asyncio
async def test_split_at_silence_short_audio():
    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
    )
    # 1 second of audio = 32000 bytes (16kHz, 16-bit)
    audio = b"\x00\x00" * 16000
    chunks = session._split_at_silence(audio)
    assert len(chunks) == 1
    assert chunks[0] == audio


@pytest.mark.asyncio
async def test_split_at_silence_long_audio():
    session = VoiceSession(
        transcription_strategy=MockTranscription(),
        correction_strategy=MockCorrection(),
    )
    # 60 seconds of audio = 1,920,000 bytes
    # Create audio with alternating loud and silent frames to ensure a split point exists
    frame_size = 960  # 30ms at 16kHz = 480 samples * 2 bytes
    frames_per_sec = 16000 * 2 // frame_size  # ~33 frames/sec

    # Build 60 seconds: first 30s loud, then 30s with quiet sections
    loud_frame = (b"\x64\x00" * (frame_size // 2))  # RMS ~100
    quiet_frame = (b"\x00\x00" * (frame_size // 2))  # RMS ~0

    total_frames = frames_per_sec * 60
    frames = []
    for i in range(total_frames):
        if i < total_frames // 2:
            frames.append(loud_frame)
        else:
            # Alternate: 10 loud, 20 quiet to create silence zones
            if (i % 30) < 10:
                frames.append(loud_frame)
            else:
                frames.append(quiet_frame)

    audio = b"".join(frames)
    chunks = session._split_at_silence(audio, target_seconds=25)
    assert len(chunks) >= 2

    # Verify reassembled == original
    reassembled = b"".join(chunks)
    assert reassembled == audio


@pytest.mark.asyncio
async def test_transcribe_long_audio_short():
    transcription = CountingTranscription()
    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=MockCorrection(),
    )
    # 1 second of audio
    audio = b"\x00\x00" * 16000
    result = await session._transcribe_long_audio(audio)
    assert transcription.call_count == 1
    assert result == "result_1"


@pytest.mark.asyncio
async def test_transcribe_long_audio_long():
    transcription = CountingTranscription()
    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=MockCorrection(),
    )
    # 60 seconds of audio with silence zones for splitting
    frame_size = 960
    frames_per_sec = 16000 * 2 // frame_size

    loud_frame = (b"\x64\x00" * (frame_size // 2))
    quiet_frame = (b"\x00\x00" * (frame_size // 2))

    total_frames = frames_per_sec * 60
    frames = []
    for i in range(total_frames):
        if i < total_frames // 2:
            frames.append(loud_frame)
        else:
            if (i % 30) < 10:
                frames.append(loud_frame)
            else:
                frames.append(quiet_frame)

    audio = b"".join(frames)
    result = await session._transcribe_long_audio(audio)
    assert transcription.call_count >= 2
    # Results joined with space
    assert "result_" in result
