import array, webrtcvad, struct, math, random

vad = webrtcvad.Vad(1)  # 尝试最不严格的模式
sr = 16000
frame_ms = 30
frame_bytes = int(sr * frame_ms / 1000) * 2

# 生成更像语音的信号：多个频率叠加 + 调制
samples = []
for i in range(sr * 2):  # 2 seconds
    t = i / sr
    # 基频 + 谐波 + 一些噪声
    v = (math.sin(2 * math.pi * 150 * t) * 0.5 +
         math.sin(2 * math.pi * 300 * t) * 0.3 +
         math.sin(2 * math.pi * 600 * t) * 0.15 +
         math.sin(2 * math.pi * 900 * t) * 0.05 +
         (random.random() - 0.5) * 0.1)
    # 振幅调制（模拟语音的包络）
    envelope = 0.5 + 0.5 * math.sin(2 * math.pi * 4 * t)
    v = int(v * envelope * 30000)
    samples.append(v)

pcm = struct.pack(f"<{len(samples)}h", *samples)

frames = []
for i in range(0, len(pcm), frame_bytes):
    frame = pcm[i:i+frame_bytes]
    if len(frame) < frame_bytes:
        break
    frames.append(vad.is_speech(frame, sr))

print(f"aggressiveness=1, frames={len(frames)}, voiced={sum(frames)}, ratio={sum(frames)/len(frames):.2%}")

# 也测试 aggressiveness=3
vad3 = webrtcvad.Vad(3)
frames3 = []
for i in range(0, len(pcm), frame_bytes):
    frame = pcm[i:i+frame_bytes]
    if len(frame) < frame_bytes:
        break
    frames3.append(vad3.is_speech(frame, sr))

print(f"aggressiveness=3, frames={len(frames3)}, voiced={sum(frames3)}, ratio={sum(frames3)/len(frames3):.2%}")

# 测试纯静音
silent = b'\x00' * frame_bytes * 50
silent_frames = []
for i in range(0, len(silent), frame_bytes):
    frame = silent[i:i+frame_bytes]
    if len(frame) < frame_bytes:
        break
    silent_frames.append(vad.is_speech(frame, sr))
print(f"silence: voiced={sum(silent_frames)} (should be 0)")
