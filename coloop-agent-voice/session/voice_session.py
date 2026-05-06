import asyncio
import time
from typing import Optional, Callable

from core.transcription_strategy import TranscriptionStrategy
from core.correction_strategy import CorrectionStrategy
from audio.energy_vad import EnergyVAD
from correction.streaming_diff import streaming_diff


class VoiceSession:
    def __init__(
        self,
        transcription_strategy: TranscriptionStrategy,
        correction_strategy: CorrectionStrategy,
        emit_callback: Optional[Callable] = None,
        language: str = "zh",
        enable_streaming_correction: bool = True,
        vad_threshold: int = 500,
        silence_timeout_ms: int = 1000,
        max_segment_ms: int = 15000,
        preview_interval_sec: float = 1.5,
        recognition_mode: str = "realtime",
    ):
        self.transcription = transcription_strategy
        self.correction = correction_strategy
        self.emit = emit_callback or (lambda _event, _payload: None)
        self.language = language
        self.enable_streaming_correction = enable_streaming_correction
        self.recognition_mode = recognition_mode

        self.vad = EnergyVAD(
            threshold=vad_threshold,
            silence_timeout_ms=silence_timeout_ms,
            max_segment_ms=max_segment_ms,
        )
        self.last_text = ""
        self.segment_index = 0
        self.full_text = ""
        self._lock = asyncio.Lock()
        self._last_preview_time = 0.0
        self._preview_interval = preview_interval_sec
        self._raw_segments: list[bytes] = []

    async def feed_audio(self, pcm_bytes: bytes):
        async with self._lock:
            segment = self.vad.process(pcm_bytes)
            if segment:
                await self._finalize_segment_audio(segment)

            if self.vad.triggered:
                now = time.monotonic()
                if now - self._last_preview_time >= self._preview_interval:
                    preview = self.vad.current_segment
                    if len(preview) >= 16000 * 1 * 2:  # at least 1 second
                        await self._preview_transcribe(preview)
                        self._last_preview_time = now

    async def _preview_transcribe(self, audio_bytes: bytes):
        try:
            text = self.transcription.transcribe(audio_bytes, language=self.language)
            if not text:
                return
            if self.enable_streaming_correction:
                diff = streaming_diff(self.last_text, text)
                if diff["changed"]:
                    await self.emit(
                        "partial",
                        {
                            "text": text,
                            "segment_index": self.segment_index,
                            "is_stable": False,
                        },
                    )
                    self.last_text = text
            else:
                if text != self.last_text:
                    await self.emit(
                        "partial",
                        {
                            "text": text,
                            "segment_index": self.segment_index,
                            "is_stable": False,
                        },
                    )
                    self.last_text = text
        except Exception as e:
            print(f"[_preview_transcribe] error: {e}")

    async def _finalize_segment_audio(self, audio_bytes: bytes):
        try:
            # Store raw audio for deferred transcription modes
            if self.recognition_mode in ("realtime_final", "final_only"):
                self._raw_segments.append(audio_bytes)

            # final_only mode: skip all per-segment processing
            if self.recognition_mode == "final_only":
                self.segment_index += 1
                self.last_text = ""
                return

            text = self.transcription.transcribe(audio_bytes, language=self.language)
            print(f"[_finalize] {len(audio_bytes)} bytes, result='{text}'")
            if not text:
                self.last_text = ""
                return

            await self.emit(
                "segment_final",
                {"text": text, "segment_index": self.segment_index},
            )

            corrected_text = text
            try:
                corrected_text = await self.correction.correct(text)
                if corrected_text != text:
                    await self.emit(
                        "post_corrected",
                        {
                            "text": corrected_text,
                            "original": text,
                            "segment_index": self.segment_index,
                        },
                    )
            except Exception:
                corrected_text = text

            self.full_text += corrected_text + " "
            self.segment_index += 1
            self.last_text = ""
            self._last_preview_time = 0.0

        except Exception as e:
            print(f"[_finalize] error: {e}")
            await self.emit("error", {"message": str(e)})

    async def finalize_segment(self):
        async with self._lock:
            segment = self.vad.flush()
            if segment:
                await self._finalize_segment_audio(segment)

    async def stop(self):
        await self.finalize_segment()

        if self.recognition_mode in ("realtime_final", "final_only"):
            if self._raw_segments:
                all_audio = b"".join(self._raw_segments)
                final_text = await self._transcribe_long_audio(all_audio)
                if final_text:
                    corrected = await self.correction.correct(final_text)
                    self.full_text = corrected + " "
                    await self.emit(
                        "complete",
                        {
                            "full_text": corrected.strip(),
                            "final_text": corrected.strip(),
                        },
                    )
                else:
                    await self.emit(
                        "complete",
                        {
                            "full_text": self.full_text.strip(),
                            "final_text": "",
                        },
                    )
            else:
                await self.emit(
                    "complete",
                    {
                        "full_text": self.full_text.strip(),
                        "final_text": "",
                    },
                )
        else:
            await self.emit("complete", {"full_text": self.full_text.strip()})

    def _split_at_silence(
        self, audio_bytes: bytes, target_seconds: int = 25
    ) -> list[bytes]:
        """Split audio at a silence point near the target duration.

        Uses RMS energy to find the quietest frame near the target split point.
        Frame size: 30ms at 16kHz = 960 bytes.
        """
        sample_rate = 16000
        bytes_per_sample = 2
        frame_ms = 30
        frame_size = int(sample_rate * frame_ms / 1000) * bytes_per_sample  # 960
        target_bytes = target_seconds * sample_rate * bytes_per_sample

        if len(audio_bytes) <= target_bytes:
            return [audio_bytes]

        # Split into frames
        num_frames = len(audio_bytes) // frame_size
        frames = [
            audio_bytes[i * frame_size : (i + 1) * frame_size]
            for i in range(num_frames)
        ]

        target_frame = target_bytes // frame_size
        search_window = 10  # +-10 frames (~300ms)

        best_idx = target_frame
        best_rms = float("inf")

        start = max(0, target_frame - search_window)
        end = min(num_frames, target_frame + search_window + 1)

        for i in range(start, end):
            rms = EnergyVAD._rms(frames[i])
            if rms < best_rms:
                best_rms = rms
                best_idx = i

        # Split at the quietest frame
        split_byte = best_idx * frame_size
        part1 = audio_bytes[:split_byte]
        part2 = audio_bytes[split_byte:]

        result = []
        if part1:
            result.append(part1)
        if part2:
            # Recursively split the second part if still too long
            result.extend(self._split_at_silence(part2, target_seconds))
        return result

    async def _transcribe_long_audio(self, audio_bytes: bytes) -> str:
        """Transcribe audio, chunking if longer than 30s."""
        thirty_seconds_bytes = 30 * 16000 * 2  # 960,000 bytes

        if len(audio_bytes) <= thirty_seconds_bytes:
            return self.transcription.transcribe(audio_bytes, language=self.language)

        chunks = self._split_at_silence(audio_bytes, target_seconds=25)
        results = []
        for chunk in chunks:
            text = self.transcription.transcribe(chunk, language=self.language)
            if text:
                results.append(text)
        return " ".join(results)
