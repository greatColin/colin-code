import httpx


class PostCorrector:
    PROMPT = """请对以下语音识别结果进行纠错和润色，修正可能的同音字错误、标点缺失，保持原意不变。只输出修正后的文本，不要解释。

原文：{text}"""

    def __init__(self, api_base: str, api_key: str, model: str):
        self.api_base = api_base.rstrip("/")
        self.api_key = api_key
        self.model = model

    async def correct(self, text: str) -> str:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{self.api_base}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "messages": [
                        {"role": "system", "content": "你是一个语音识别后处理纠错助手。"},
                        {"role": "user", "content": self.PROMPT.format(text=text)},
                    ],
                    "temperature": 0.3,
                    "max_tokens": 1024,
                },
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"].strip()
