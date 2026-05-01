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

const SAMPLE_RATE = 16000;

async function startRecording() {
    try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: { sampleRate: SAMPLE_RATE, channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
        audioContext = new AudioContext({ sampleRate: SAMPLE_RATE });

        await audioContext.audioWorklet.addModule("audio-processor.js");

        const source = audioContext.createMediaStreamSource(mediaStream);
        workletNode = new AudioWorkletNode(audioContext, "pcm-processor");

        socket = io();

        socket.on("connect", () => {
            socket.emit("start", {
                config: {
                    lang: "zh",
                    enable_streaming_correction: streamingToggle.classList.contains("active"),
                    enable_post_correction: postToggle.classList.contains("active"),
                }
            });
            statusText.textContent = "正在录音...";
        });

        socket.on("partial", (data) => {
            realtimeText.textContent = data.text;
            realtimeText.classList.add("has-content");
        });

        socket.on("segment_final", (data) => {
            realtimeText.textContent = "";
            realtimeText.classList.remove("has-content");
            resultArea.textContent += data.text;
        });

        socket.on("post_corrected", (data) => {
            const current = resultArea.textContent;
            if (current.endsWith(data.original)) {
                resultArea.textContent = current.slice(0, -data.original.length) + data.text;
            }
        });

        socket.on("complete", (data) => {
            statusText.textContent = "录音完成";
        });

        socket.on("error", (data) => {
            statusText.textContent = "错误: " + data.message;
            console.error("Error:", data.message);
        });

        workletNode.port.onmessage = (e) => {
            if (socket && socket.connected) {
                socket.emit("audio", e.data);
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
