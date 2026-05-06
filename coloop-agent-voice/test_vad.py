import array, webrtcvad

vad = webrtcvad.Vad(3)
sr = 16000
frame_ms = 30
frame_bytes = int(sr * frame_ms / 1000) * 2
print(f"frame_bytes={frame_bytes}")

# 生成一个 1 秒的正弦波测试
import struct, math
samples = []
for i in range(sr):
    v = int(math.sin(2 * math.pi * 440 * i / sr) * 30000)
    samples.append(v)

pcm = struct.pack(f"<{len(samples)}h", *samples)
print(f"test pcm len={len(pcm)}")

# 切分帧并测试
frames = []
for i in range(0, len(pcm), frame_bytes):
    frame = pcm[i:i+frame_bytes]
    if len(frame) < frame_bytes:
        break
    is_speech = vad.is_speech(frame, sr)
    frames.append(is_speech)

print(f"frames={len(frames)}, voiced={sum(frames)}, unvoiced={len(frames)-sum(frames)}")
print(f"first 10: {frames[:10]}")
