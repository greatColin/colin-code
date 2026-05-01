def streaming_diff(old_text: str, new_text: str) -> dict:
    """Compare two text strings and return difference metadata."""
    if old_text == new_text:
        return {"changed": False, "current": new_text}

    return {
        "changed": True,
        "current": new_text,
        "previous": old_text,
    }
