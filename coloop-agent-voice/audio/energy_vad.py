import array


class EnergyVAD:
    """Simple energy-based voice activity detector."""

    def __init__(
        self,
        sample_rate: int = 16000,
        frame_ms: int = 30,
        threshold: int = 500,
        silence_timeout_ms: int = 1000,
        max_segment_ms: int = 15000,
    ):
        self.sample_rate = sample_rate
        self.frame_bytes = int(sample_rate * frame_ms / 1000) * 2
        self.threshold = threshold
        self.silence_timeout_frames = max(1, silence_timeout_ms // frame_ms)
        self.max_segment_bytes = int(sample_rate * max_segment_ms / 1000) * 2
        self._partial = bytearray()
        self.triggered = False
        self.silence_count = 0
        self._buffer = bytearray()

    @staticmethod
    def _rms(frame: bytes) -> int:
        arr = array.array("h", frame)
        if not arr:
            return 0
        # Avoid overflow: use integer arithmetic
        total = sum(x * x for x in arr)
        return int((total / len(arr)) ** 0.5)

    def process(self, pcm_bytes: bytes) -> bytes | None:
        """Feed audio bytes. Returns a complete segment when speech ends, else None."""
        self._partial.extend(pcm_bytes)
        frames = []
        while len(self._partial) >= self.frame_bytes:
            frame = bytes(self._partial[: self.frame_bytes])
            frames.append(frame)
            del self._partial[: self.frame_bytes]

        result = None
        for frame in frames:
            rms = self._rms(frame)
            if self.triggered:
                self._buffer.extend(frame)
                if rms < self.threshold:
                    self.silence_count += 1
                    if self.silence_count >= self.silence_timeout_frames:
                        result = bytes(self._buffer)
                        self._buffer = bytearray()
                        self.triggered = False
                        self.silence_count = 0
                        return result
                else:
                    self.silence_count = 0
                    # Force segment if too long
                    if len(self._buffer) >= self.max_segment_bytes:
                        result = bytes(self._buffer)
                        self._buffer = bytearray()
                        self.silence_count = 0
                        # Stay triggered for next segment
                        self.triggered = True
                        return result
            else:
                if rms >= self.threshold:
                    self.triggered = True
                    self._buffer.extend(frame)
                    self.silence_count = 0

        return None

    @property
    def current_segment(self) -> bytes:
        return bytes(self._buffer)

    def flush(self) -> bytes | None:
        """Return any remaining buffered audio."""
        if len(self._buffer) > 0:
            result = bytes(self._buffer)
            self._buffer = bytearray()
            self.triggered = False
            self.silence_count = 0
            return result
        return None
