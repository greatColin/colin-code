import pytest
from unittest.mock import AsyncMock, Mock, patch

from correction.post_corrector import PostCorrector


class TestPostCorrector:
    @pytest.mark.asyncio
    async def test_correct_success(self):
        corrector = PostCorrector("https://api.test.com", "test-key", "gpt-4")

        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "choices": [{"message": {"content": "corrected text"}}]
        }
        mock_response.raise_for_status = Mock()

        with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
            mock_post.return_value = mock_response
            result = await corrector.correct("raw text")
            assert result == "corrected text"

            call_args = mock_post.call_args
            assert call_args[1]["headers"]["Authorization"] == "Bearer test-key"
            assert "raw text" in call_args[1]["json"]["messages"][1]["content"]

    @pytest.mark.asyncio
    async def test_correct_api_error(self):
        corrector = PostCorrector("https://api.test.com", "test-key", "gpt-4")

        with patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_post:
            mock_post.side_effect = Exception("API error")
            with pytest.raises(Exception, match="API error"):
                await corrector.correct("raw text")
