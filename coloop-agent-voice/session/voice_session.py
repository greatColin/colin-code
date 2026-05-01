import asyncio
from typing import Optional, Callable

from engine.whisper_engine import WhisperEngine
from audio.vad_processor import VADProcessor
from correction.streaming_diff import streaming_diff
from correction.post_corrector import PostCorrector


class VoiceSession:
    def __init__(
        self,
        config: dict,
        engine: WhisperEngine,
        post_corrector: Optional[PostCorrector] = None,
        emit_callback: Optional[Callable] = None,
    ):
        self.config = config
        self.engine = engine
        self.post_corrector = post_corrector
        self.emit = emit_callback or (lambda _event, _payload: None)

        self.vad = VADProcessor()
        self.last_text = ""
        self.segment_index = 0
        self.full_text = ""
        self._lock = asyncio.Lock()

    async def feed_audio(self, pcm_bytes: bytes):
        async with self._lock:
            segment = self.vad.process(pcm_bytes)
            if segment:
                await self._transcribe_segment(segment)

    async def _transcribe_segment(self, audio_bytes: bytes):
        try:
            text = self.engine.transcribe(
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
                self.last_text = text

        except Exception as e:
            await self.emit("error", {"message": str(e)})

    async def finalize_segment(self):
        async with self._lock:
            segment = self.vad.flush()
            if not segment:
                return

            text = self.engine.transcribe(
                segment, language=self.config.get("lang", "zh")
            )
            if not text:
                return

            self.last_text = text
            await self.emit(
                "segment_final",
                {"text": text, "segment_index": self.segment_index},
            )

            corrected_text = text
            if self.post_corrector and self.config.get("enable_post_correction", False):
                try:
                    corrected_text = await self.post_corrector.correct(text)
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

    async def stop(self):
        await self.finalize_segment()
        await self.emit("complete", {"full_text": self.full_text.strip()})
