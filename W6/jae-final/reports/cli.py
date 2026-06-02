"""Top-level CLI for Codex report tools."""

from __future__ import annotations

import argparse
import sys

from . import rag
from .summarize import add_summarize_arguments, run_self_test as run_summarize_self_test
from .summarize import run_summarize


def run_all_self_tests() -> int:
    checks = [
        run_summarize_self_test(),
        rag.run_self_test(),
    ]
    if all(code == 0 for code in checks):
        print("self-test passed: reports")
        return 0
    print("self-test failed: reports", file=sys.stderr)
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="작업 보고서 요약/RAG CLI")
    parser.add_argument("--self-test", action="store_true")
    subparsers = parser.add_subparsers(dest="command")

    summarize_parser = subparsers.add_parser("summarize", help="최근/날짜 보고서를 바로 요약")
    add_summarize_arguments(summarize_parser)
    summarize_parser.set_defaults(func=run_summarize)

    index_parser = subparsers.add_parser("index", help="보고서를 Chroma에 색인")
    rag.add_index_arguments(index_parser)
    index_parser.set_defaults(func=rag.index_reports)

    reindex_parser = subparsers.add_parser("reindex", help="Chroma collection을 재생성하고 전체 재색인")
    rag.add_index_arguments(reindex_parser)
    reindex_parser.set_defaults(func=rag.reindex_reports)

    search_parser = subparsers.add_parser("search", help="RAG 검색 결과만 출력")
    rag.add_search_arguments(search_parser)
    search_parser.set_defaults(func=rag.run_search)

    ask_parser = subparsers.add_parser("ask", help="RAG 검색 근거로 질문 답변")
    rag.add_ask_arguments(ask_parser)
    ask_parser.set_defaults(func=rag.answer_question)

    stats_parser = subparsers.add_parser("stats", help="Chroma collection 상태 출력")
    rag.add_stats_arguments(stats_parser)
    stats_parser.set_defaults(func=rag.stats)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.self_test:
        return run_all_self_tests()
    if not hasattr(args, "func"):
        parser.print_help()
        return 1
    return int(args.func(args))
