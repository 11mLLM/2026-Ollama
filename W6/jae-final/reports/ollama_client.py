"""Small stdlib Ollama HTTP client."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any


DEFAULT_MODEL = "llama3.1"
DEFAULT_EMBED_MODEL = "embeddinggemma"
DEFAULT_OLLAMA_URL = "http://localhost:11434"


class OllamaError(RuntimeError):
    """Raised when Ollama cannot serve a request."""


class OllamaPreflightError(OllamaError):
    """Raised when Ollama or a required model is unavailable before generation."""


class OllamaGenerateError(OllamaError):
    """Raised when Ollama fails during text generation."""


class OllamaEmbedError(OllamaError):
    """Raised when Ollama fails during embedding generation."""


def normalize_ollama_url(value: str) -> str:
    return value.rstrip("/")


def ollama_json_request(
    base_url: str,
    path: str,
    *,
    payload: dict[str, Any] | None = None,
    timeout: int,
    error_cls: type[OllamaError] = OllamaError,
) -> Any:
    url = f"{normalize_ollama_url(base_url)}{path}"
    data = None
    method = "GET"
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        method = "POST"
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except (TimeoutError, urllib.error.URLError, OSError) as exc:
        raise error_cls(f"Ollama에 연결할 수 없습니다: {url}: {exc}") from exc

    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise error_cls(f"Ollama 응답 JSON을 파싱할 수 없습니다: {url}: {exc}") from exc


def model_matches(requested: str, available: str) -> bool:
    if requested == available:
        return True
    if ":" not in requested:
        return available == f"{requested}:latest" or available.split(":", 1)[0] == requested
    return False


def ensure_model_available(base_url: str, model: str, *, timeout: int) -> None:
    payload = ollama_json_request(base_url, "/api/tags", timeout=timeout, error_cls=OllamaPreflightError)
    models = payload.get("models") if isinstance(payload, dict) else None
    names = [str(item.get("name")) for item in models if isinstance(item, dict) and item.get("name")] if models else []
    if any(model_matches(model, name) for name in names):
        return
    installed = ", ".join(names) if names else "none"
    raise OllamaPreflightError(
        f"Ollama model을 찾을 수 없습니다: {model}\n"
        f"- installed models: {installed}\n"
        f"- install: ollama pull {model}"
    )


def generate(base_url: str, model: str, prompt: str, *, timeout: int) -> str:
    payload = ollama_json_request(
        base_url,
        "/api/generate",
        payload={
            "model": model,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": 0.1},
        },
        timeout=timeout,
        error_cls=OllamaGenerateError,
    )
    if not isinstance(payload, dict) or not isinstance(payload.get("response"), str):
        raise OllamaGenerateError("Ollama generate 응답에 response 문자열이 없습니다")
    return payload["response"].strip()


def embed(base_url: str, model: str, texts: list[str], *, timeout: int) -> list[list[float]]:
    payload = ollama_json_request(
        base_url,
        "/api/embed",
        payload={"model": model, "input": texts},
        timeout=timeout,
        error_cls=OllamaEmbedError,
    )
    embeddings = payload.get("embeddings") if isinstance(payload, dict) else None
    if not isinstance(embeddings, list):
        raise OllamaEmbedError("Ollama embed 응답에 embeddings 배열이 없습니다")
    result: list[list[float]] = []
    for embedding in embeddings:
        if not isinstance(embedding, list):
            raise OllamaEmbedError("Ollama embed 응답 embedding 형식이 올바르지 않습니다")
        result.append([float(value) for value in embedding])
    return result

