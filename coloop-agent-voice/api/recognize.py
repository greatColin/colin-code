import time
from fastapi import APIRouter, UploadFile, File, Form
from typing import Optional

router = APIRouter()


@router.post("/api/recognize")
async def recognize(
    audio: UploadFile = File(...),
    enable_correction: bool = Form(False),
):
    """
    接收音频文件，返回识别结果。

    Request:
      - audio: 音频文件 (webm/opus 或 wav)
      - enable_correction: 是否启用 LLM 纠错

    Response:
      {
        "text": "最终识别结果",
        "raw_text": "原始识别结果",
        "corrected": false,
        "duration_ms": 1234
      }
    """
    # TODO: 实际识别逻辑在 Task 3 实现
    return {
        "text": "placeholder",
        "raw_text": "placeholder",
        "corrected": False,
        "duration_ms": 0,
    }
