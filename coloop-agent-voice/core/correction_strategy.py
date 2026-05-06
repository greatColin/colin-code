from abc import ABC, abstractmethod


class CorrectionStrategy(ABC):
    """文本纠错策略接口"""

    @abstractmethod
    async def correct(self, text: str) -> str:
        """对识别文本进行纠错"""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """策略名称，用于日志和配置标识"""
        ...
