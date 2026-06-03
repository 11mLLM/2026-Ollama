from __future__ import annotations

from app.vlm.qwen_client import call_ollama_chat


EXAONE_MODEL = "hf.co/LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-GGUF:Q4_K_M"


def generate_with_exaone(prompt: str, model: str = EXAONE_MODEL, timeout_sec: int = 120) -> str:
    payload = {
        "model": model,
        "stream": False,
        "messages": [{"role": "user", "content": prompt}],
        "options": {
            "temperature": 0.7,
            "top_p": 0.9,
            "repeat_penalty": 1.08,
        },
    }
    return call_ollama_chat(payload, timeout_sec=timeout_sec)


def chat_with_exaone(
    messages: list[dict],
    model: str = EXAONE_MODEL,
    temperature: float = 0.6,
    top_p: float = 0.9,
    num_ctx: int = 8192,
    timeout_sec: int = 180,
) -> str:
    """Multi-turn chat for the coach. ``messages`` is a list of
    {"role": "system"|"user"|"assistant", "content": str}."""
    payload = {
        "model": model,
        "stream": False,
        "messages": messages,
        "options": {
            "temperature": temperature,
            "top_p": top_p,
            "repeat_penalty": 1.08,
            "num_ctx": num_ctx,
        },
    }
    return call_ollama_chat(payload, timeout_sec=timeout_sec)
