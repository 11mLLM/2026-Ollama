"""Thin Ollama text-chat helper shared by the agentic planner.

Kept separate from the VLM/EXAONE clients so text-only reasoning (query
planning, routing) has one place to live and one JSON-extraction helper.
"""
from __future__ import annotations

import json
import re


class LLMUnavailable(RuntimeError):
    """Raised when the text LLM backend cannot be reached."""


def chat_text(
    prompt: str,
    model: str,
    temperature: float = 0.0,
    num_ctx: int = 8192,
) -> str:
    """Single-turn text chat through Ollama. Raises LLMUnavailable on failure."""
    try:
        import ollama
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise LLMUnavailable("ollama package not installed") from exc
    try:
        response = ollama.chat(
            model=model,
            messages=[{"role": "user", "content": prompt}],
            options={"temperature": temperature, "num_ctx": num_ctx},
        )
        return response["message"]["content"]
    except Exception as exc:  # noqa: BLE001 - uniform backend failure
        raise LLMUnavailable(f"Ollama chat failed: {exc}") from exc


def extract_json(text: str) -> dict | None:
    """Best-effort extraction of the first JSON object from a model response."""
    if not text:
        return None
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    candidate = fenced.group(1) if fenced else None
    if candidate is None:
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            candidate = text[start : end + 1]
    if candidate is None:
        return None
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        return None
