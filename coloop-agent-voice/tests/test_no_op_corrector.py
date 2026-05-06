import pytest
from correction.no_op_corrector import NoOpCorrectionStrategy


@pytest.mark.asyncio
async def test_correct_returns_text_unchanged():
    s = NoOpCorrectionStrategy()
    result = await s.correct("你好世界")
    assert result == "你好世界"


@pytest.mark.asyncio
async def test_correct_returns_empty_string():
    s = NoOpCorrectionStrategy()
    result = await s.correct("")
    assert result == ""


def test_get_name():
    s = NoOpCorrectionStrategy()
    assert s.get_name() == "none"
