import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from core.correction_strategy import CorrectionStrategy


class NoOpCorrectionStrategy(CorrectionStrategy):
    """不纠错，直接返回原文"""

    async def correct(self, text: str) -> str:
        return text

    def get_name(self) -> str:
        return "none"
