import asyncio
import socketio
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from factory import VoiceFactory
from session.voice_session import VoiceSession

# Default setting file path
DEFAULT_SETTING_FILE = "../coloop-agent-core/src/main/resources/coloop-agent-setting.json"

factory = VoiceFactory(setting_file=DEFAULT_SETTING_FILE)
sio = socketio.AsyncServer(
    async_mode="asgi",
    cors_allowed_origins="*",
    max_http_buffer_size=10_000_000,
    ping_timeout=60,
    ping_interval=25,
)
app = FastAPI()
socket_app = socketio.ASGIApp(sio, app)

# Static files
app.mount("/static", StaticFiles(directory="static"), name="static")


@app.get("/api/config")
async def get_config():
    return {
        "language": factory.config.get("language"),
        "recognitionMode": factory.config.get("recognitionMode", "realtime"),
        "enableStreamingCorrection": factory.config.get("enableStreamingCorrection"),
        "enablePostCorrection": factory.config.get("enablePostCorrection"),
        "coloopWsUrl": factory.config.get_coloop_ws_url(),
    }


sessions = {}

# Pre-create transcription strategy (may need lazy loading for heavy models)
_transcription_strategy = None
_transcription_lock = asyncio.Lock()


async def get_transcription_strategy():
    global _transcription_strategy
    if _transcription_strategy is None:
        async with _transcription_lock:
            if _transcription_strategy is None:
                print("[strategy] creating transcription strategy, please wait...")
                loop = asyncio.get_event_loop()
                _transcription_strategy = await loop.run_in_executor(
                    None, factory.create_transcription
                )
                print(f"[strategy] ready: {_transcription_strategy.get_name()}")
    return _transcription_strategy


@sio.event
async def connect(sid, environ):
    print(f"Client connected: {sid}")


@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")
    sessions.pop(sid, None)


@sio.on("start")
async def on_start(sid, data):
    transcription = await get_transcription_strategy()

    session_config = data.get("config", {})
    enable_post_correction = session_config.get(
        "enable_post_correction", factory.config.get("enablePostCorrection")
    )

    # If post correction disabled by frontend, force no-op
    if enable_post_correction:
        correction = factory.create_correction()
    else:
        from correction.no_op_corrector import NoOpCorrectionStrategy
        correction = NoOpCorrectionStrategy()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session = VoiceSession(
        transcription_strategy=transcription,
        correction_strategy=correction,
        emit_callback=emit,
        language=factory.config.get("language"),
        enable_streaming_correction=session_config.get(
            "enable_streaming_correction",
            factory.config.get("enableStreamingCorrection"),
        ),
        recognition_mode=session_config.get(
            "recognition_mode",
            factory.config.get("recognitionMode", "realtime"),
        ),
        vad_threshold=session_config.get("vad_threshold", 500),
        silence_timeout_ms=session_config.get("silence_timeout_ms", 1000),
        max_segment_ms=session_config.get("max_segment_ms", 15000),
        preview_interval_sec=session_config.get("preview_interval_sec", 1.5),
    )
    sessions[sid] = session


@sio.on("audio")
async def on_audio(sid, data):
    if sid in sessions:
        print(f"[audio] sid={sid[:8]} len={len(data)}")
        await sessions[sid].feed_audio(data)
    else:
        print(f"[audio] no session for sid={sid[:8]}")


@sio.on("stop")
async def on_stop(sid, data=None):
    session = sessions.pop(sid, None)
    if session:
        await session.stop()


@app.on_event("startup")
async def startup_event():
    print("[startup] preloading transcription strategy...")
    await get_transcription_strategy()
    print("[startup] ready")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(socket_app, host=factory.config.host, port=factory.config.port)
