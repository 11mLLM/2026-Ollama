"""Ollama embedding client for the Agentic RAG.

Wraps the local ``nomic-embed-text`` model served by Ollama. All calls are
defensive: if Ollama or the model is unavailable, callers can fall back to the
keyword retriever instead of crashing the pipeline.
"""
from __future__ import annotations

import os

# nomic-embed-text is reliable in Ollama. bge-m3 has stronger Korean semantics
# but some quantizations return NaN on certain Korean inputs in current Ollama
# builds; the keyword layer in the retriever already handles literal name/term
# matches, so nomic is the safe default. Override via OWCS_EMBED_MODEL.
# NOTE: the ChromaDB index must be rebuilt when this changes (vector dims differ).
EMBED_MODEL = os.environ.get("OWCS_EMBED_MODEL", "nomic-embed-text")


class EmbeddingUnavailable(RuntimeError):
    """Raised when the embedding backend cannot be reached."""


def _is_finite_vector(vec: list[float]) -> bool:
    return bool(vec) and all(v == v and v not in (float("inf"), float("-inf")) for v in vec)


def embed_texts(texts: list[str], model: str = EMBED_MODEL) -> list[list[float]]:
    """Return one embedding vector per input text.

    Embeds one document at a time. Some Ollama embedding builds (e.g. certain
    bge-m3 quantizations) return NaN when given a batch ``input`` list; embedding
    singly avoids that and lets us detect a bad vector for one specific document.

    Raises EmbeddingUnavailable if the backend is missing or a vector is non-finite.
    """
    if not texts:
        return []
    try:
        import ollama
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise EmbeddingUnavailable("ollama package not installed") from exc

    vectors: list[list[float]] = []
    for idx, text in enumerate(texts):
        try:
            response = ollama.embed(model=model, input=text)
            embeddings = response.get("embeddings") if isinstance(response, dict) else None
            vec = list(embeddings[0]) if embeddings else None
            if vec is None:
                single = ollama.embeddings(model=model, prompt=text)
                vec = list(single["embedding"])
        except Exception as exc:  # noqa: BLE001 - uniform backend failure
            raise EmbeddingUnavailable(
                f"Ollama embedding call failed at item {idx}: {exc}"
            ) from exc
        if not _is_finite_vector(vec):
            raise EmbeddingUnavailable(
                f"Embedding model {model} returned a non-finite vector for item {idx}"
            )
        vectors.append(vec)
    return vectors


def embed_query(text: str, model: str = EMBED_MODEL) -> list[float]:
    """Embed a single query string."""
    result = embed_texts([text], model=model)
    return result[0] if result else []


def is_available(model: str = EMBED_MODEL) -> bool:
    """Cheap probe used to decide between semantic and keyword retrieval."""
    try:
        return len(embed_query("테스트", model=model)) > 0
    except EmbeddingUnavailable:
        return False


if __name__ == "__main__":
    print("embedding available:", is_available())
