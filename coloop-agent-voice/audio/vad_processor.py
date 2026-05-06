import collections
import webrtcvad


class VADProcessor:
    def __init__(self, aggressiveness: int = 3, sample_rate: int = 16000, frame_duration_ms: int = 30, ring_buffer_maxlen: int = 50):
        self.vad = webrtcvad.Vad(aggressiveness)
        self.sample_rate = sample_rate
        self.frame_duration_ms = frame_duration_ms
        self.frame_bytes = int(sample_rate * frame_duration_ms / 1000) * 2
        self.ring_buffer = collections.deque(maxlen=ring_buffer_maxlen)
        self.triggered = False
        self._buffer = bytearray()
        self._partial = bytearray()


    def process(self, pcm_bytes: bytes) -> bytes | None:
        # Append new bytes to any leftover partial frame
        self._partial.extend(pcm_bytes)

        frames = []
        while len(self._partial) >= self.frame_bytes:
            frame = bytes(self._partial[:self.frame_bytes])
            frames.append((frame, self.vad.is_speech(frame, self.sample_rate)))
            del self._partial[:self.frame_bytes]

        for frame, is_speech in frames:
            if not self.triggered:
                self.ring_buffer.append((frame, is_speech))
                num_voiced = sum(1 for _, s in self.ring_buffer if s)
                if (
                    len(self.ring_buffer) >= self.ring_buffer.maxlen
                    and num_voiced > 0.9 * len(self.ring_buffer)
                ):
                    self.triggered = True
                    self._buffer.extend(b"".join(f for f, _ in self.ring_buffer))
                    self.ring_buffer.clear()
            else:
                self._buffer.extend(frame)
                self.ring_buffer.append((frame, is_speech))
                num_unvoiced = sum(1 for _, s in self.ring_buffer if not s)
                if (
                    len(self.ring_buffer) >= self.ring_buffer.maxlen
                    and num_unvoiced > 0.9 * len(self.ring_buffer)
                ):
                    self.triggered = False
                    result = bytes(self._buffer)
                    self._buffer = bytearray()
                    self.ring_buffer.clear()
                    return result

        return None

    def flush(self) -> bytes | None:
        if self.triggered and len(self._buffer) > 0:
            result = bytes(self._buffer)
            self._buffer = bytearray()
            self.triggered = False
            self.ring_buffer.clear()
            return result
        return None
