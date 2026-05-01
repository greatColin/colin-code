const micBtn = document.getElementById("micBtn");
const statusText = document.getElementById("statusText");
const realtimeText = document.getElementById("realtimeText");
const resultArea = document.getElementById("resultArea");
const settingsToggle = document.getElementById("settingsToggle");
const settingsPanel = document.getElementById("settingsPanel");
const streamingToggle = document.getElementById("streamingToggle");
const postToggle = document.getElementById("postToggle");

let socket = null;
let audioContext = null;
let mediaStream = null;
let workletNode = null;
let isRecording = false;
let analyser = null;
let animationId = null;
let envelopeHistory = [];

const SAMPLE_RATE = 16000;
const MAX_HISTORY = 120; // ~2 seconds at 60fps

const waveformCanvas = document.getElementById("waveform");
const waveformCtx = waveformCanvas.getContext("2d");

function drawWaveform() {
    if (!analyser) return;

    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
    analyser.getByteTimeDomainData(dataArray);

    let peak = 0;
    for (let i = 0; i < bufferLength; i++) {
        const v = Math.abs(dataArray[i] - 128) / 128;
        if (v > peak) peak = v;
    }

    envelopeHistory.push(peak);
    if (envelopeHistory.length > MAX_HISTORY) {
        envelopeHistory.shift();
    }

    const width = waveformCanvas.width;
    const height = waveformCanvas.height;
    const centerY = height / 2;

    waveformCtx.clearRect(0, 0, width, height);

    // Fill area
    waveformCtx.beginPath();
    waveformCtx.moveTo(0, centerY);
    for (let i = 0; i < envelopeHistory.length; i++) {
        const x = (i / (MAX_HISTORY - 1)) * width;
        const y = centerY - envelopeHistory[i] * centerY * 0.85;
        waveformCtx.lineTo(x, y);
    }
    for (let i = envelopeHistory.length - 1; i >= 0; i--) {
        const x = (i / (MAX_HISTORY - 1)) * width;
        const y = centerY + envelopeHistory[i] * centerY * 0.85;
        waveformCtx.lineTo(x, y);
    }
    waveformCtx.closePath();
    waveformCtx.fillStyle = "rgba(255, 255, 255, 0.12)";
    waveformCtx.fill();

    // Stroke line
    waveformCtx.beginPath();
    for (let i = 0; i < envelopeHistory.length; i++) {
        const x = (i / (MAX_HISTORY - 1)) * width;
        const y = centerY - envelopeHistory[i] * centerY * 0.85;
        if (i === 0) waveformCtx.moveTo(x, y);
        else waveformCtx.lineTo(x, y);
    }
    waveformCtx.strokeStyle = "rgba(255, 255, 255, 0.7)";
    waveformCtx.lineWidth = 1.5;
    waveformCtx.lineJoin = "round";
    waveformCtx.stroke();

    animationId = requestAnimationFrame(drawWaveform);
}

async function startRecording() {
    try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: { sampleRate: SAMPLE_RATE, channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
        audioContext = new AudioContext({ sampleRate: SAMPLE_RATE });

        await audioContext.audioWorklet.addModule("audio-processor.js");

        const source = audioContext.createMediaStreamSource(mediaStream);
        workletNode = new AudioWorkletNode(audioContext, "pcm-processor");

        analyser = audioContext.createAnalyser();
        analyser.fftSize = 256;
        source.connect(analyser);

        socket = io({ transports: ["websocket"] });

        socket.on("connect", () => {
            console.log("[socket] connected");
            socket.emit("start", {
                config: {
                    lang: "zh",
                    enable_streaming_correction: streamingToggle.classList.contains("active"),
                    enable_post_correction: postToggle.classList.contains("active"),
                }
            });
            console.log("[socket] start emitted");
            statusText.textContent = "正在录音...";
            waveformCanvas.classList.add("active");
            envelopeHistory = [];
            drawWaveform();
        });

        socket.on("partial", (data) => {
            console.log("[socket] partial:", data);
            realtimeText.textContent = data.text;
            realtimeText.classList.add("has-content");
        });

        socket.on("segment_final", (data) => {
            console.log("[socket] segment_final:", data);
            realtimeText.textContent = "";
            realtimeText.classList.remove("has-content");
            resultArea.textContent += data.text;
        });

        socket.on("post_corrected", (data) => {
            console.log("[socket] post_corrected:", data);
            const current = resultArea.textContent;
            if (current.endsWith(data.original)) {
                resultArea.textContent = current.slice(0, -data.original.length) + data.text;
            }
        });

        socket.on("complete", (data) => {
            console.log("[socket] complete:", data);
            statusText.textContent = "录音完成";
        });

        socket.on("error", (data) => {
            console.log("[socket] error:", data);
            statusText.textContent = "错误: " + data.message;
        });

        socket.on("disconnect", (reason) => {
            console.log("[socket] disconnect:", reason);
        });

        workletNode.port.onmessage = (e) => {
            console.log("[audio] worklet packet, bytes:", e.data.byteLength);
            if (socket && socket.connected) {
                socket.emit("audio", e.data);
                console.log("[audio] emitted to socket");
            } else {
                console.log("[audio] socket not ready, dropped");
            }
        };

        source.connect(workletNode);
        workletNode.connect(audioContext.destination);

        isRecording = true;
        micBtn.classList.add("recording");
        micBtn.textContent = "⏹️";

    } catch (err) {
        console.error("Failed to start recording:", err);
        statusText.textContent = "无法访问麦克风，请检查权限";
    }
}

function stopRecording() {
    if (socket) {
        socket.emit("stop");
        socket.disconnect();
        socket = null;
    }
    if (workletNode) {
        workletNode.disconnect();
        workletNode = null;
    }
    if (analyser) {
        analyser.disconnect();
        analyser = null;
    }
    if (animationId) {
        cancelAnimationFrame(animationId);
        animationId = null;
    }
    if (audioContext) {
        audioContext.close();
        audioContext = null;
    }
    if (mediaStream) {
        mediaStream.getTracks().forEach((t) => t.stop());
        mediaStream = null;
    }
    isRecording = false;
    micBtn.classList.remove("recording");
    micBtn.textContent = "🎙️";
    realtimeText.textContent = "";
    realtimeText.classList.remove("has-content");
    waveformCanvas.classList.remove("active");
    if (waveformCtx) {
        waveformCtx.clearRect(0, 0, waveformCanvas.width, waveformCanvas.height);
    }
    statusText.textContent = "点击麦克风开始录音";
}

micBtn.addEventListener("click", () => {
    if (isRecording) {
        stopRecording();
    } else {
        startRecording();
    }
});

document.getElementById("copyBtn").addEventListener("click", () => {
    navigator.clipboard
        .writeText(resultArea.textContent)
        .then(() => {
            statusText.textContent = "已复制到剪贴板";
            setTimeout(() => { if (!isRecording) statusText.textContent = "点击麦克风开始录音"; }, 2000);
        })
        .catch(() => statusText.textContent = "复制失败");
});

document.getElementById("clearBtn").addEventListener("click", () => {
    resultArea.textContent = "";
    statusText.textContent = "已清空";
    setTimeout(() => { if (!isRecording) statusText.textContent = "点击麦克风开始录音"; }, 1500);
});

settingsToggle.addEventListener("click", () => {
    settingsPanel.classList.toggle("open");
});

function setupToggle(el) {
    el.addEventListener("click", () => el.classList.toggle("active"));
}

setupToggle(streamingToggle);
setupToggle(postToggle);
