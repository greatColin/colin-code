import asyncio
import socketio
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from config import VoiceConfig
from voice_factory import VoiceFactory
from session.voice_session import VoiceSession

config = VoiceConfig()
factory = VoiceFactory(config)

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

# Global strategy instances (lazy-loaded)
_transcription_strategy = None
_correction_strategy = None
_strategy_lock = asyncio.Lock()


async def get_transcription_strategy():
    global _transcription_strategy
    if _transcription_strategy is None:
        async with _strategy_lock:
            if _transcription_strategy is None:
                print("[strategy] loading transcription strategy, please wait...")
                loop = asyncio.get_event_loop()
                _transcription_strategy = await loop.run_in_executor(
                    None, factory.create_transcription_strategy
                )
                print(f"[strategy] loaded: {_transcription_strategy.get_name()}")
    return _transcription_strategy


def get_correction_strategy():
    global _correction_strategy
    if _correction_strategy is None:
        try:
            _correction_strategy = factory.create_correction_strategy()
            print(f"[strategy] loaded correction: {_correction_strategy.get_name()}")
        except Exception as e:
            print(f"[strategy] correction strategy not available: {e}")
    return _correction_strategy


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
    transcription = await get_transcription_strategy()
    correction = get_correction_strategy()

    async def emit(event, payload):
        await sio.emit(event, payload, room=sid)

    session = VoiceSession(
        config=data.get("config", {}),
        transcription_strategy=transcription,
        correction_strategy=correction,
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
    print("[startup] preloading transcription strategy...")
    await get_transcription_strategy()
    print("[startup] strategy ready")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(socket_app, host=config.host, port=config.port)
