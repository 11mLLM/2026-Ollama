"""Unified knowledge-base loader for the Agentic RAG.

Loads documents from both the legacy ``app/rag/knowledge_base.jsonl`` (caster
style / tactic snippets) and every ``app/rag/kb/*.jsonl`` domain file
(heroes, maps, league, teams, players, terms). Documents are de-duplicated by
``id`` so the same snippet is never indexed twice.

Each document is a dict::

    {"id": str, "text": str, "metadata": {"collection": str, ...}}
"""
from __future__ import annotations

import json
from pathlib import Path

# Project paths (this file lives in app/rag/)
RAG_DIR = Path(__file__).resolve().parent
KB_DIR = RAG_DIR / "kb"
LEGACY_KB = RAG_DIR / "knowledge_base.jsonl"


def _read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    docs: list[dict] = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        try:
            docs.append(json.loads(line))
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid JSON in {path}:{line_no}: {exc}") from exc
    return docs


def load_documents(
    kb_dir: Path = KB_DIR,
    legacy_kb: Path = LEGACY_KB,
) -> list[dict]:
    """Load and de-duplicate all KB documents.

    Domain files under ``kb/`` take priority over legacy entries with the same id.
    """
    documents: dict[str, dict] = {}

    # Legacy snippets first so domain files can override by id.
    for doc in _read_jsonl(legacy_kb):
        doc_id = doc.get("id")
        if doc_id:
            documents[doc_id] = doc

    if kb_dir.exists():
        for jsonl_path in sorted(kb_dir.glob("*.jsonl")):
            for doc in _read_jsonl(jsonl_path):
                doc_id = doc.get("id")
                if not doc_id:
                    continue
                documents[doc_id] = doc

    return list(documents.values())


def collection_counts(documents: list[dict]) -> dict[str, int]:
    """Count documents per ``metadata.collection`` for reporting."""
    counts: dict[str, int] = {}
    for doc in documents:
        collection = (doc.get("metadata") or {}).get("collection", "uncategorized")
        counts[collection] = counts.get(collection, 0) + 1
    return counts


if __name__ == "__main__":
    docs = load_documents()
    print(f"Loaded {len(docs)} documents")
    for collection, count in sorted(collection_counts(docs).items()):
        print(f"  {collection}: {count}")
