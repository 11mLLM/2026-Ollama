import base64
import io

import mss
from PIL import Image

from config import SCREENSHOT_MAX_EDGE


def capture_screen_base64():
    with mss.mss() as sct:
        monitor = sct.monitors[1]
        raw = sct.grab(monitor)
        image = Image.frombytes("RGB", raw.size, raw.bgra, "raw", "BGRX")
    image.thumbnail((SCREENSHOT_MAX_EDGE, SCREENSHOT_MAX_EDGE))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("ascii")


if __name__ == "__main__":
    encoded = capture_screen_base64()
    print(f"captured screenshot, base64 length = {len(encoded)}")
