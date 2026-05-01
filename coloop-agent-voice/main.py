import asyncio
import socketio
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from config import VoiceConfig
from engine.whisper_engine import WhisperEngine
from correction.post_corrector import PostCorrector
from session.voice_session import VoiceSession

config = VoiceConfig()
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

# Global engine instance (lazy-loaded)
_engine = None
_engine_lock = asyncio.Lock()


def _create_engine():
    return WhisperEngine(
        config.whisper_model,
        device=config.whisper_device,
        compute_type=config.whisper_compute_type,
    )


async def get_engine():
    global _engine
    if _engine is None:
        async with _engine_lock:
            if _engine is None:
                print("[engine] loading whisper model, please wait...")
                loop = asyncio.get_event_loop()
                _engine = await loop.run_in_executor(None, _create_engine)
                print("[engine] model loaded")
    return _engine


def get_post_corrector():
    if not config.enable_post_correction:
        return None
    model_cfg = config.get_post_correction_config()
    if model_cfg:
        return PostCorrector(
            model_cfg["api_base"],
            model_cfg["api_key"],
            model_cfg["model"],
        )
    return None


sessions = {}


@sio.event
async def connect(sid, environ):
    print(f"Client connected: {sid}")


@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")
    sessions.pop(sid, None)


@sio.on("start")
async def on_start(sid, data):
    engine = await get_engine()
    corrector = get_post_corrector()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session = VoiceSession(
        config=data.get("config", {}),
        engine=engine,
        post_corrector=corrector,
        emit_callback=emit,
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
    print("[startup] preloading whisper model...")
    await get_engine()
    print("[startup] model ready")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(socket_app, host=config.host, port=config.port)
