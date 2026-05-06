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
    ):
        self.transcription = transcription_strategy
        self.correction = correction_strategy
        self.emit = emit_callback or (lambda _event, _payload: None)
        self.language = language
        self.enable_streaming_correction = enable_streaming_correction

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
        await self.emit("complete", {"full_text": self.full_text.strip()})
