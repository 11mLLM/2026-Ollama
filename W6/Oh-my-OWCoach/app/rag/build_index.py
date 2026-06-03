"""Build the ChromaDB index for the Agentic RAG.

Loads every KB document (heroes, maps, league, teams, players, terms, style),
embeds the ``text`` field with the Ollama ``nomic-embed-text`` model, and stores
them in a persistent ChromaDB collection. The ``metadata.collection`` field is
kept as a filterable attribute so the retriever can route queries to a subset
of collections (e.g. only ``players`` + ``teams``).

Usage::

    python -m app.rag.build_index
    python -m app.rag.build_index --reset
"""
from __future__ import annotations

import argparse
from pathlib import Path

from app.rag.embeddings import EMBED_MODEL, EmbeddingUnavailable, embed_texts
from app.rag.kb_loader import collection_counts, load_documents

RAG_DIR = Path(__file__).resolve().parent
CHROMA_DIR = RAG_DIR / "chroma_db"
CHROMA_COLLECTION = "owcs_kb"


def _flatten_metadata(metadata: dict) -> dict:
    """ChromaDB only accepts scalar metadata values; coerce lists/None."""
    flat: dict[str, str | int | float | bool] = {}
    for key, value in metadata.items():
        if value is None:
            continue
        if isinstance(value, (str, int, float, bool)):
            flat[key] = value
        else:
            flat[key] = str(value)
    return flat


def build_index(reset: bool = False, embed_model: str = EMBED_MODEL) -> dict:
    """Embed all KB docs and persist them in ChromaDB. Returns a summary dict."""
    documents = load_documents()
    if not documents:
        raise RuntimeError("No KB documents found to index.")

    try:
        import chromadb
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise RuntimeError(
            "chromadb is not installed. Run: pip install chromadb"
        ) from exc

    ids = [doc["id"] for doc in documents]
    texts = [doc["text"] for doc in documents]
    metadatas = [_flatten_metadata(doc.get("metadata") or {}) for doc in documents]

    # Embed first so a backend failure aborts before we touch the store.
    embeddings = embed_texts(texts, model=embed_model)
    if len(embeddings) != len(texts):
        raise EmbeddingUnavailable(
            f"Embedding count {len(embeddings)} != document count {len(texts)}"
        )

    CHROMA_DIR.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    if reset:
        try:
            client.delete_collection(CHROMA_COLLECTION)
        except Exception:  # noqa: BLE001 - collection may not exist yet
            pass

    collection = client.get_or_create_collection(
        name=CHROMA_COLLECTION,
        metadata={"hnsw:space": "cosine", "embed_model": embed_model},
    )
    collection.upsert(ids=ids, embeddings=embeddings, documents=texts, metadatas=metadatas)

    return {
        "indexed": len(ids),
        "collections": collection_counts(documents),
        "chroma_path": str(CHROMA_DIR),
        "embed_model": embed_model,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the OWCS RAG ChromaDB index.")
    parser.add_argument("--reset", action="store_true", help="Drop and rebuild the collection.")
    args = parser.parse_args()

    try:
        summary = build_index(reset=args.reset)
    except EmbeddingUnavailable as exc:
        print(f"[build_index] Embedding backend unavailable: {exc}")
        print("[build_index] Ensure the model is pulled (e.g. `ollama pull nomic-embed-text`).")
        print("[build_index] Retrieval still works in keyword-only mode without an index.")
        return 1
    except RuntimeError as exc:
        print(f"[build_index] {exc}")
        return 1

    print(f"[build_index] Indexed {summary['indexed']} documents into {summary['chroma_path']}")
    for collection, count in sorted(summary["collections"].items()):
        print(f"  {collection}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
