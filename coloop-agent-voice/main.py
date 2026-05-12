from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from api.recognize import router as recognize_router

app = FastAPI()
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
    uvicorn.run(app, host="0.0.0.0", port=8000)
