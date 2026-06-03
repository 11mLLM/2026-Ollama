from __future__ import annotations

import base64
import json
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


QWEN_VLM_MODEL = "hf.co/jc-builds/Qwen3.5-9B-VLM-Q4_K_M-GGUF:Q4_K_M"
OLLAMA_CHAT_URL = "http://localhost:11434/api/chat"


def analyze_frame_with_qwen_vlm(
    prompt: str,
    image_path: Path,
    model: str = QWEN_VLM_MODEL,
    timeout_sec: int = 180,
) -> str:
    payload = {
        "model": model,
        "stream": False,
        "messages": [
            {
                "role": "user",
                "content": prompt,
                "images": [base64.b64encode(image_path.read_bytes()).decode("ascii")],
            }
        ],
        "options": {"temperature": 0.0, "top_p": 0.9},
    }
    return call_ollama_chat(payload, timeout_sec=timeout_sec)


def call_ollama_chat(payload: dict, timeout_sec: int = 120) -> str:
    request = Request(
        OLLAMA_CHAT_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=timeout_sec) as response:
            body = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama chat request failed: HTTP {exc.code}: {error_body}") from exc
    except URLError as exc:
        raise RuntimeError(f"Ollama chat request failed: {exc}") from exc
    except TimeoutError as exc:
        raise RuntimeError("Ollama chat request timed out.") from exc

    message = body.get("message") or {}
    content = message.get("content")
    if not isinstance(content, str):
        raise RuntimeError(f"Unexpected Ollama response: {body}")
    return content
