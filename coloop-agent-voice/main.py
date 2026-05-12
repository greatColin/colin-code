import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from api.recognize import router as recognize_router, set_engine
from factory import VoiceFactory

DEFAULT_SETTING_FILE = "../coloop-agent-core/src/main/resources/coloop-agent-setting.json"
factory = VoiceFactory(setting_file=DEFAULT_SETTING_FILE)

_transcription_strategy = None
_correction_strategy = None


async def init_engines():
    global _transcription_strategy, _correction_strategy
    loop = asyncio.get_event_loop()
    _transcription_strategy = await loop.run_in_executor(None, factory.create_transcription)
    _correction_strategy = factory.create_correction()
    set_engine(_transcription_strategy, _correction_strategy)


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("[startup] loading whisper model...")
    await init_engines()
    print("[startup] ready")
    yield


app = FastAPI(lifespan=lifespan)
app.mount("/static", StaticFiles(directory="static"), name="static")
app.include_router(recognize_router)


@app.get("/api/config")
async def get_config():
    return {
        "enableCorrection": False,
        "postAction": "none",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=factory.config.host, port=factory.config.port)
