import asyncio
import time
import tempfile
import os
from fastapi import APIRouter, UploadFile, File, Form

router = APIRouter()

_transcription = None
_correction = None


def set_engine(transcription, correction):
    global _transcription, _correction
    _transcription = transcription
    _correction = correction


@router.post("/api/recognize")
def recognize(
    audio: UploadFile = File(...),
    enable_correction: bool = Form(False),
):
    """接收音频文件，返回识别结果。"""
    if _transcription is None:
        return {"text": "", "raw_text": "", "corrected": False, "duration_ms": 0, "error": "引擎未初始化"}

    start_time = time.time()

    # Save temp file - faster-whisper can handle various formats via ffmpeg
    suffix = ".webm" if "webm" in (audio.content_type or "") else ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = audio.file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # Pass file path to engine - faster-whisper uses ffmpeg for format conversion
        raw_text = _transcription.transcribe(tmp_path, language="zh")
        duration_ms = int((time.time() - start_time) * 1000)

        if not raw_text:
            return {"text": "", "raw_text": "", "corrected": False, "duration_ms": duration_ms}

        # Correction
        text = raw_text
        corrected = False
        if enable_correction and _correction:
            try:
                corrected_text = asyncio.run(_correction.correct(raw_text))
                if corrected_text != raw_text:
                    text = corrected_text
                    corrected = True
            except Exception as e:
                print(f"[recognize] correction error: {e}")

        return {
            "text": text,
            "raw_text": raw_text,
            "corrected": corrected,
            "duration_ms": duration_ms,
        }

    finally:
        os.unlink(tmp_path)
