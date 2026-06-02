"""Chroma-backed RAG over local Codex reports."""

from __future__ import annotations

import argparse
import hashlib
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .ollama_client import DEFAULT_EMBED_MODEL, DEFAULT_MODEL, DEFAULT_OLLAMA_URL, OllamaEmbedError, OllamaError
from .ollama_client import OllamaGenerateError, OllamaPreflightError, embed, ensure_model_available, generate
from .prompts import answer_prompt, fenced, trim_text
from .discovery import DEFAULT_PROJECT_ROOT, ReportError, all_report_groups, choose_reports_dir, positive_int
from .discovery import read_report_document, rel, report_file_metadata, resolve_project_root


DEFAULT_DB_DIR = Path(__file__).resolve().parents[1] / ".data" / "chroma"
DEFAULT_COLLECTION = "reports_v1"
DEFAULT_TOP_K = 6
DEFAULT_MAX_CHUNK_CHARS = 1400
DEFAULT_MAX_CONTEXT_CHARS = 18_000


class DependencyError(RuntimeError):
    """Raised when an optional RAG dependency is missing."""


@dataclass(frozen=True)
class DocumentChunk:
    id: str
    text: str
    metadata: dict[str, str | int | bool]


@dataclass(frozen=True)
class SearchResult:
    text: str
    metadata: dict[str, Any]
    distance: float | None


def load_chromadb() -> Any:
    try:
        import chromadb  # type: ignore[import-not-found]
    except ImportError as exc:
        raise DependencyError(
            "chromadb가 설치되어 있지 않습니다.\n"
            "- install: python3 -m pip install -r requirements.txt"
        ) from exc
    return chromadb


def get_collection(db_dir: Path, collection_name: str) -> Any:
    chromadb = load_chromadb()
    db_dir.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(db_dir))
    return client.get_or_create_collection(name=collection_name, embedding_function=None)


def delete_collection_if_exists(db_dir: Path, collection_name: str) -> None:
    chromadb = load_chromadb()
    db_dir.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(db_dir))
    try:
        client.delete_collection(collection_name)
    except Exception:
        return


def split_paragraphs(text: str) -> list[str]:
    paragraphs: list[str] = []
    current: list[str] = []
    for line in text.splitlines():
        if line.strip():
            current.append(line)
            continue
        if current:
            paragraphs.append("\n".join(current).strip())
            current = []
    if current:
        paragraphs.append("\n".join(current).strip())
    return paragraphs or [text.strip()]


def chunk_text(text: str, *, max_chars: int) -> list[str]:
    chunks: list[str] = []
    current = ""
    for paragraph in split_paragraphs(text):
        if not paragraph:
            continue
        if len(paragraph) > max_chars:
            if current:
                chunks.append(current.strip())
                current = ""
            for start in range(0, len(paragraph), max_chars):
                chunks.append(paragraph[start : start + max_chars].strip())
            continue
        candidate = f"{current}\n\n{paragraph}".strip() if current else paragraph
        if len(candidate) <= max_chars:
            current = candidate
        else:
            if current:
                chunks.append(current.strip())
            current = paragraph
    if current:
        chunks.append(current.strip())
    return chunks


def deterministic_chunk_id(project_root: Path, report_path: Path, chunk_index: int) -> str:
    raw = f"{project_root.resolve()}|{report_path.resolve()}|{report_path.stat().st_mtime_ns}|{chunk_index}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def build_chunks(project_root: Path, reports_dir: Path, *, max_chunk_chars: int) -> list[DocumentChunk]:
    chunks: list[DocumentChunk] = []
    for group in all_report_groups(project_root, reports_dir):
        for path in group.files:
            document = read_report_document(path)
            metadata = report_file_metadata(path, project_root, group.slug)
            for chunk_index, text in enumerate(chunk_text(document, max_chars=max_chunk_chars)):
                chunk_metadata = dict(metadata)
                chunk_metadata["chunk_index"] = chunk_index
                chunks.append(
                    DocumentChunk(
                        id=deterministic_chunk_id(project_root, path, chunk_index),
                        text=text,
                        metadata=chunk_metadata,
                    )
                )
    return chunks


def batch(items: list[Any], size: int) -> list[list[Any]]:
    return [items[index : index + size] for index in range(0, len(items), size)]


def index_reports(args: argparse.Namespace) -> int:
    project_root = resolve_project_root(args.project_dir)
    reports_dir = choose_reports_dir(args.reports_dir, project_root)
    try:
        chunks = build_chunks(project_root, reports_dir, max_chunk_chars=args.max_chunk_chars)
    except ReportError as exc:
        print(str(exc), file=sys.stderr)
        return 5

    report_paths = sorted({str(chunk.metadata["report_path"]) for chunk in chunks})
    if args.dry_run:
        print("# Reports RAG index dry-run")
        print(f"- reports_dir: `{rel(reports_dir, project_root)}`")
        print(f"- report_files: {len(report_paths)}")
        print(f"- chunks: {len(chunks)}")
        return 0

    try:
        ensure_model_available(args.ollama_url, args.embed_model, timeout=args.timeout)
        collection = get_collection(args.db_dir, args.collection)
        for report_path in report_paths:
            collection.delete(where={"report_path": report_path})
        for chunk_batch in batch(chunks, args.batch_size):
            embeddings = embed(
                args.ollama_url,
                args.embed_model,
                [chunk.text for chunk in chunk_batch],
                timeout=args.timeout,
            )
            collection.add(
                ids=[chunk.id for chunk in chunk_batch],
                embeddings=embeddings,
                documents=[chunk.text for chunk in chunk_batch],
                metadatas=[chunk.metadata for chunk in chunk_batch],
            )
    except DependencyError as exc:
        print(str(exc), file=sys.stderr)
        return 6
    except OllamaPreflightError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except OllamaEmbedError as exc:
        print(str(exc), file=sys.stderr)
        return 4

    print("# Reports RAG index")
    print(f"- reports_dir: `{rel(reports_dir, project_root)}`")
    print(f"- db_dir: `{args.db_dir}`")
    print(f"- collection: `{args.collection}`")
    print(f"- report_files: {len(report_paths)}")
    print(f"- chunks: {len(chunks)}")
    return 0


def reindex_reports(args: argparse.Namespace) -> int:
    try:
        delete_collection_if_exists(args.db_dir, args.collection)
    except DependencyError as exc:
        print(str(exc), file=sys.stderr)
        return 6
    return index_reports(args)


def search_reports(args: argparse.Namespace) -> tuple[int, list[SearchResult]]:
    try:
        ensure_model_available(args.ollama_url, args.embed_model, timeout=args.timeout)
        collection = get_collection(args.db_dir, args.collection)
        query_embedding = embed(args.ollama_url, args.embed_model, [args.query], timeout=args.timeout)[0]
        raw = collection.query(
            query_embeddings=[query_embedding],
            n_results=args.top_k,
            include=["documents", "metadatas", "distances"],
        )
    except DependencyError as exc:
        print(str(exc), file=sys.stderr)
        return 6, []
    except OllamaPreflightError as exc:
        print(str(exc), file=sys.stderr)
        return 2, []
    except OllamaEmbedError as exc:
        print(str(exc), file=sys.stderr)
        return 4, []
    except Exception as exc:
        print(f"Chroma 검색에 실패했습니다: {exc}", file=sys.stderr)
        return 7, []

    documents = raw.get("documents", [[]])[0] if isinstance(raw, dict) else []
    metadatas = raw.get("metadatas", [[]])[0] if isinstance(raw, dict) else []
    distances = raw.get("distances", [[]])[0] if isinstance(raw, dict) else []
    results: list[SearchResult] = []
    for index, document in enumerate(documents):
        metadata = metadatas[index] if index < len(metadatas) and isinstance(metadatas[index], dict) else {}
        distance = float(distances[index]) if index < len(distances) and distances[index] is not None else None
        results.append(SearchResult(text=str(document), metadata=metadata, distance=distance))
    return 0, results


def print_search_results(results: list[SearchResult]) -> None:
    print("# Reports RAG 검색 결과")
    if not results:
        print("- 관련 chunk 없음")
        return
    for index, result in enumerate(results, 1):
        metadata = result.metadata
        distance = "unknown" if result.distance is None else f"{result.distance:.4f}"
        print(f"\n## {index}. {metadata.get('report_slug', 'unknown')}")
        print(f"- path: `{metadata.get('report_path', 'unknown')}`")
        print(f"- role: {metadata.get('file_role', 'unknown')}")
        print(f"- distance: {distance}")
        print(trim_text(result.text, 900))


def run_search(args: argparse.Namespace) -> int:
    code, results = search_reports(args)
    if code != 0:
        return code
    print_search_results(results)
    return 0


def answer_question(args: argparse.Namespace) -> int:
    code, results = search_reports(args)
    if code != 0:
        return code
    if not results:
        print("# 답변\n\n## 요약\n관련 보고서를 찾지 못했습니다.")
        return 0

    context_parts: list[str] = []
    for index, result in enumerate(results, 1):
        metadata = result.metadata
        context_parts.append(
            "\n".join(
                [
                    f"## 근거 {index}",
                    f"- report_slug: {metadata.get('report_slug', 'unknown')}",
                    f"- report_path: {metadata.get('report_path', 'unknown')}",
                    f"- file_role: {metadata.get('file_role', 'unknown')}",
                    f"- distance: {result.distance}",
                    fenced("markdown", result.text),
                ]
            )
        )
    context = trim_text("\n\n---\n\n".join(context_parts), args.max_context_chars)

    try:
        ensure_model_available(args.ollama_url, args.model, timeout=args.timeout)
        markdown = generate(args.ollama_url, args.model, answer_prompt(args.query, context), timeout=args.timeout)
    except OllamaPreflightError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except (OllamaGenerateError, OllamaError) as exc:
        print(str(exc), file=sys.stderr)
        return 4

    print(markdown)
    return 0


def stats(args: argparse.Namespace) -> int:
    try:
        collection = get_collection(args.db_dir, args.collection)
        count = collection.count()
        raw = collection.get(include=["metadatas"])
    except DependencyError as exc:
        print(str(exc), file=sys.stderr)
        return 6
    except Exception as exc:
        print(f"Chroma stats 조회에 실패했습니다: {exc}", file=sys.stderr)
        return 7

    metadatas = raw.get("metadatas", []) if isinstance(raw, dict) else []
    report_paths = {metadata.get("report_path") for metadata in metadatas if isinstance(metadata, dict)}
    print("# Reports RAG stats")
    print(f"- db_dir: `{args.db_dir}`")
    print(f"- collection: `{args.collection}`")
    print(f"- chunks: {count}")
    print(f"- report_files: {len(report_paths)}")
    return 0


def run_self_test() -> int:
    with tempfile.TemporaryDirectory() as temp_name:
        project_root = Path(temp_name)
        reports_dir = project_root / ".codex" / "harness" / "reports"
        reports_dir.mkdir(parents=True)
        report = reports_dir / "sample-backend-gate.md"
        report.write_text(
            "상태: Fail\n\n검증:\n- `backendQualityCheck` = not run\n\n잔여 위험: evidence missing\n",
            encoding="utf-8",
        )
        chunks = build_chunks(project_root, reports_dir, max_chunk_chars=40)
        checks = [
            len(chunks) >= 2,
            chunks[0].metadata["report_slug"] == "sample",
            chunks[0].metadata["file_role"] == "backend-gate",
            deterministic_chunk_id(project_root, report, 0) == chunks[0].id,
            "backendQualityCheck" in " ".join(chunk.text for chunk in chunks),
        ]
    if all(checks):
        print("self-test passed: rag_reports")
        return 0
    print("self-test failed: rag_reports", file=sys.stderr)
    return 1


def add_rag_common_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--project-dir",
        type=Path,
        default=DEFAULT_PROJECT_ROOT,
        help=f"분석 대상 프로젝트 루트 (default: {DEFAULT_PROJECT_ROOT})",
    )
    parser.add_argument("--reports-dir", type=Path, help="보고서 디렉터리")
    parser.add_argument("--db-dir", type=Path, default=DEFAULT_DB_DIR, help=f"Chroma DB 경로 (default: {DEFAULT_DB_DIR})")
    parser.add_argument("--collection", default=DEFAULT_COLLECTION, help=f"Chroma collection (default: {DEFAULT_COLLECTION})")
    parser.add_argument("--ollama-url", default=DEFAULT_OLLAMA_URL, help=f"Ollama URL (default: {DEFAULT_OLLAMA_URL})")
    parser.add_argument("--embed-model", default=DEFAULT_EMBED_MODEL, help=f"Embedding model (default: {DEFAULT_EMBED_MODEL})")
    parser.add_argument("--timeout", type=positive_int, default=120, help="Ollama 요청 timeout seconds")


def add_index_arguments(parser: argparse.ArgumentParser) -> None:
    add_rag_common_arguments(parser)
    parser.add_argument("--dry-run", action="store_true", help="Chroma/Ollama 호출 없이 색인 대상만 출력")
    parser.add_argument("--max-chunk-chars", type=positive_int, default=DEFAULT_MAX_CHUNK_CHARS)
    parser.add_argument("--batch-size", type=positive_int, default=16)


def add_search_arguments(parser: argparse.ArgumentParser) -> None:
    add_rag_common_arguments(parser)
    parser.add_argument("query")
    parser.add_argument("--top-k", type=positive_int, default=DEFAULT_TOP_K)


def add_ask_arguments(parser: argparse.ArgumentParser) -> None:
    add_search_arguments(parser)
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"Answer model (default: {DEFAULT_MODEL})")
    parser.add_argument("--max-context-chars", type=positive_int, default=DEFAULT_MAX_CONTEXT_CHARS)


def add_stats_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--db-dir", type=Path, default=DEFAULT_DB_DIR, help=f"Chroma DB 경로 (default: {DEFAULT_DB_DIR})")
    parser.add_argument("--collection", default=DEFAULT_COLLECTION, help=f"Chroma collection (default: {DEFAULT_COLLECTION})")
