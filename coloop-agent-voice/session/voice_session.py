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
        self._audio_buffer = bytearray()

    async def feed_audio(self, pcm_bytes: bytes):
        import array
        arr = array.array('h', pcm_bytes)
        max_val = max(abs(x) for x in arr) if arr else 0
        print(f"[feed_audio] {len(pcm_bytes)} bytes, max_amp={max_val}")
        async with self._lock:
            self._audio_buffer.extend(pcm_bytes)
            # 累积约 2 秒音频后转录（绕过 VAD 测试）
            if len(self._audio_buffer) >= 16000 * 2 * 2:
                segment = bytes(self._audio_buffer)
                self._audio_buffer = bytearray()
                await self._transcribe_segment(segment)

    async def _transcribe_segment(self, audio_bytes: bytes):
        try:
            print(f"[_transcribe] {len(audio_bytes)} bytes, lang={self.config.get('lang', 'zh')}")
            text = self.engine.transcribe(
                audio_bytes, language=self.config.get("lang", "zh")
            )
            print(f"[_transcribe] result='{text}'")
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
            print(f"[_transcribe] error: {e}")
            await self.emit("error", {"message": str(e)})

    async def finalize_segment(self):
        async with self._lock:
            if len(self._audio_buffer) == 0:
                return
            segment = bytes(self._audio_buffer)
            self._audio_buffer = bytearray()

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
