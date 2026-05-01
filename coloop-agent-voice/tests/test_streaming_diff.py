from correction.streaming_diff import streaming_diff


class TestStreamingDiff:
    def test_no_change(self):
        result = streaming_diff("hello world", "hello world")
        assert result["changed"] is False
        assert result["current"] == "hello world"

    def test_simple_change(self):
        result = streaming_diff("hello worl", "hello world")
        assert result["changed"] is True
        assert result["current"] == "hello world"
        assert result["previous"] == "hello worl"

    def test_chinese_text(self):
        result = streaming_diff("今天天气", "今天天气很好")
        assert result["changed"] is True
        assert result["current"] == "今天天气很好"
        assert result["previous"] == "今天天气"

    def test_empty_to_text(self):
        result = streaming_diff("", "first result")
        assert result["changed"] is True
        assert result["current"] == "first result"
