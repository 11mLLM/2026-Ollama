import base64
import sys

import requests

from config import OLLAMA_URL, VISION_MODEL

VISION_PROMPT = "Describe what is shown on this screen in one detailed sentence."

UNCLEAR_SCENE = "The screen content could not be clearly recognized."


def _is_garbage(text):
    if len(text) < 12:
        return True
    if " " not in text:
        return True
    letters = sum(character.isalpha() or character.isspace() for character in text)
    if letters / len(text) < 0.6:
        return True
    return False


def _call_vision(image_base64):
    response = requests.post(
        f"{OLLAMA_URL}/api/generate",
        json={
            "model": VISION_MODEL,
            "prompt": VISION_PROMPT,
            "images": [image_base64],
            "stream": False,
        },
        timeout=120,
    )
    response.raise_for_status()
    return response.json()["response"].strip()


def perceive(image_base64):
    description = _call_vision(image_base64)
    if _is_garbage(description):
        description = _call_vision(image_base64)
        if _is_garbage(description):
            return UNCLEAR_SCENE
    return description


if __name__ == "__main__":
    if len(sys.argv) > 1:
        with open(sys.argv[1], "rb") as handle:
            encoded = base64.b64encode(handle.read()).decode("ascii")
    else:
        from capture import capture_screen_base64

        encoded = capture_screen_base64()
    print(perceive(encoded))
