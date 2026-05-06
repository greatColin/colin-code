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
        config: dict,
        transcription_strategy: TranscriptionStrategy,
        correction_strategy: Optional[CorrectionStrategy] = None,
        emit_callback: Optional[Callable] = None,
    ):
        self.config = config
        self.transcription_strategy = transcription_strategy
        self.correction_strategy = correction_strategy
        self.emit = emit_callback or (lambda _event, _payload: None)

        self.vad = EnergyVAD(
            threshold=config.get("vad_threshold", 500),
            silence_timeout_ms=config.get("silence_timeout_ms", 1000),
            max_segment_ms=config.get("max_segment_ms", 15000),
        )
        self.last_text = ""
        self.segment_index = 0
        self.full_text = ""
        self._lock = asyncio.Lock()
        self._last_preview_time = 0.0
        self._preview_interval = config.get("preview_interval_sec", 1.5)

    async def feed_audio(self, pcm_bytes: bytes):
        async with self._lock:
            segment = self.vad.process(pcm_bytes)
            if segment:
                # Natural segment boundary detected
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
            text = self.transcription_strategy.transcribe(
                audio_bytes, language=self.config.get("lang", "zh")
            )
            if not text:
                return
            if self.config.get("enable_streaming_correction", True):
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
            text = self.transcription_strategy.transcribe(
                audio_bytes, language=self.config.get("lang", "zh")
            )
            print(f"[_finalize] {len(audio_bytes)} bytes, result='{text}'")
            if not text:
                self.last_text = ""
                return

            await self.emit(
                "segment_final",
                {"text": text, "segment_index": self.segment_index},
            )

            corrected_text = text
            if self.correction_strategy and self.config.get("enable_post_correction", False):
                try:
                    corrected_text = await self.correction_strategy.correct(text)
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
