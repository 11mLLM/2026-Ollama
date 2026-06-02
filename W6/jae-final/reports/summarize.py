"""Non-RAG summarization CLI behavior."""

from __future__ import annotations

import argparse
import os
import sys
import tempfile
from datetime import date, datetime
from pathlib import Path

from .ollama_client import DEFAULT_MODEL, DEFAULT_OLLAMA_URL, OllamaError, OllamaGenerateError, OllamaPreflightError
from .ollama_client import ensure_model_available, generate
from .prompts import batch_prompt, fenced, final_batch_prompt, summary_prompt, trim_text
from .discovery import DEFAULT_PROJECT_ROOT, ReportError, ReportGroup, Selection, choose_reports_dir, eligible_report_files
from .discovery import group_report_files, local_mtime, parse_iso_date, positive_int, read_report_document, rel
from .discovery import report_role, resolve_project_root, select_groups


DEFAULT_MAX_INPUT_CHARS = 45_000


def group_context(group: ReportGroup, selection: Selection, *, max_chars: int) -> str:
    lines = [
        f"## REPORT_GROUP: {group.slug}",
        f"- modified_at: {group.modified_at.isoformat(timespec='seconds')}",
        f"- files: {len(group.files)}",
    ]
    for path in group.files:
        role = report_role(path, group.slug)
        language = "json" if path.suffix == ".json" else "markdown"
        lines.extend(
            [
                "",
                f"### FILE: {rel(path, selection.project_root)}",
                f"- role: {role}",
                f"- modified_at: {local_mtime(path).isoformat(timespec='seconds')}",
                fenced(language, read_report_document(path)),
            ]
        )
    return trim_text("\n".join(lines), max_chars)


def all_groups_context(selection: Selection, *, max_chars: int) -> str:
    return "\n\n---\n\n".join(group_context(group, selection, max_chars=max_chars) for group in selection.groups)


def selection_markdown(selection: Selection) -> str:
    lines = [
        "# Codex 보고서 선택 결과",
        "",
        f"- reports_dir: `{rel(selection.reports_dir, selection.project_root)}`",
        f"- selection: {selection.reason}",
        f"- groups: {len(selection.groups)}",
    ]
    for group in selection.groups:
        lines.append(
            f"- `{group.slug}`: {group.modified_at.isoformat(timespec='seconds')}, files={len(group.files)}"
        )
        for path in group.files:
            lines.append(f"  - `{rel(path, selection.project_root)}`")
    return "\n".join(lines)


def summarize_with_ollama(selection: Selection, args: argparse.Namespace) -> str:
    ensure_model_available(args.ollama_url, args.model, timeout=args.timeout)
    combined_context = all_groups_context(selection, max_chars=args.max_input_chars)
    if len(combined_context) <= args.max_input_chars:
        return generate(
            args.ollama_url,
            args.model,
            summary_prompt(selection, combined_context),
            timeout=args.timeout,
        )

    summaries: list[str] = []
    for group in selection.groups:
        context = group_context(group, selection, max_chars=args.max_input_chars)
        summary = generate(
            args.ollama_url,
            args.model,
            batch_prompt(group, context),
            timeout=args.timeout,
        )
        summaries.append(f"## {group.slug}\n\n{summary}")

    intermediate = trim_text("\n\n---\n\n".join(summaries), args.max_input_chars)
    return generate(
        args.ollama_url,
        args.model,
        final_batch_prompt(selection, intermediate),
        timeout=args.timeout,
    )


def write_or_print(markdown: str, output: Path | None, project_root: Path) -> None:
    if output is None:
        print(markdown)
        return
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(markdown.rstrip() + "\n", encoding="utf-8")
    print(f"summary written: {rel(output, project_root)}")


def run_self_test() -> int:
    with tempfile.TemporaryDirectory() as temp_name:
        temp_root = Path(temp_name)
        reports_dir = temp_root / ".codex" / "harness" / "reports"
        reports_dir.mkdir(parents=True)

        alpha_md = reports_dir / "alpha.md"
        alpha_json = reports_dir / "alpha.json"
        alpha_pr = reports_dir / "alpha-pr-body.md"
        beta_md = reports_dir / "beta.md"
        beta_gate = reports_dir / "beta-frontend-gate.md"
        ignored = reports_dir / ".gitkeep"

        alpha_md.write_text("# Alpha\n\n상태: Pass\n검증: pass\n", encoding="utf-8")
        alpha_json.write_text(
            '{"status":"Pass","preset":"runtime-smoke","targets":"backend",'
            '"verification":{"git diff --check":{"status":"pass"}},'
            '"links":{"pr_url":"https://example.test/pr/1"},"extra":"must be omitted"}',
            encoding="utf-8",
        )
        alpha_pr.write_text("## 요약\nAlpha PR body\n", encoding="utf-8")
        beta_md.write_text("# Beta\n\n상태: Fail\n", encoding="utf-8")
        beta_gate.write_text("상태: Fail\n잔여 위험: evidence missing\n", encoding="utf-8")
        ignored.write_text("", encoding="utf-8")

        alpha_time = datetime(2026, 5, 31, 12, 0).timestamp()
        beta_time = datetime(2026, 5, 30, 12, 0).timestamp()
        for path in (alpha_md, alpha_json, alpha_pr):
            os.utime(path, (alpha_time, alpha_time))
        for path in (beta_md, beta_gate):
            os.utime(path, (beta_time, beta_time))

        chosen = choose_reports_dir(None, temp_root)
        groups = group_report_files(eligible_report_files(chosen))
        by_date = select_groups(temp_root, chosen, selected_date=date(2026, 5, 31), recent=None)
        recent = select_groups(temp_root, chosen, selected_date=None, recent=1)
        compact_json = read_report_document(alpha_json)
        context = group_context(by_date.groups[0], by_date, max_chars=10_000)

        checks = [
            chosen == reports_dir,
            {group.slug for group in groups} == {"alpha", "beta"},
            [group.slug for group in by_date.groups] == ["alpha"],
            [group.slug for group in recent.groups] == ["alpha"],
            "must be omitted" not in compact_json,
            '"status": "Pass"' in compact_json,
            "alpha-pr-body.md" in context,
            ".gitkeep" not in selection_markdown(by_date),
        ]
    if all(checks):
        print("self-test passed: summarize_reports")
        return 0
    print("self-test failed: summarize_reports", file=sys.stderr)
    return 1


def add_summarize_arguments(parser: argparse.ArgumentParser) -> None:
    selector = parser.add_mutually_exclusive_group()
    selector.add_argument("--date", type=parse_iso_date, help="선택할 보고서 mtime 날짜(YYYY-MM-DD)")
    selector.add_argument("--recent", type=positive_int, help="최신 N개 작업 그룹 선택")
    parser.add_argument(
        "--project-dir",
        type=Path,
        default=DEFAULT_PROJECT_ROOT,
        help=f"분석 대상 프로젝트 루트 (default: {DEFAULT_PROJECT_ROOT})",
    )
    parser.add_argument("--reports-dir", type=Path, help="보고서 디렉터리")
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"Ollama 모델명 (default: {DEFAULT_MODEL})")
    parser.add_argument("--ollama-url", default=DEFAULT_OLLAMA_URL, help=f"Ollama URL (default: {DEFAULT_OLLAMA_URL})")
    parser.add_argument("--output", type=Path, help="지정 시 Markdown 요약 파일 저장")
    parser.add_argument("--dry-run", action="store_true", help="Ollama 호출 없이 선택 파일만 출력")
    parser.add_argument("--max-input-chars", type=positive_int, default=DEFAULT_MAX_INPUT_CHARS)
    parser.add_argument("--timeout", type=positive_int, default=120, help="Ollama 요청 timeout seconds")
    parser.add_argument("--self-test", action="store_true")


def run_summarize(args: argparse.Namespace) -> int:
    if getattr(args, "self_test", False):
        return run_self_test()

    project_root = resolve_project_root(args.project_dir)
    reports_dir = choose_reports_dir(args.reports_dir, project_root)
    selection = select_groups(project_root, reports_dir, selected_date=args.date, recent=args.recent)
    if not selection.groups:
        print(
            f"선택된 보고서가 없습니다: reports_dir={rel(reports_dir, project_root)}, selection={selection.reason}",
            file=sys.stderr,
        )
        return 3

    if args.dry_run:
        print(selection_markdown(selection))
        return 0

    try:
        markdown = summarize_with_ollama(selection, args)
    except ReportError as exc:
        print(str(exc), file=sys.stderr)
        return 5
    except OllamaPreflightError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except OllamaGenerateError as exc:
        print(str(exc), file=sys.stderr)
        return 4
    except OllamaError as exc:
        print(str(exc), file=sys.stderr)
        return 4

    write_or_print(markdown, args.output, project_root)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Ollama로 Codex 작업 보고서를 Markdown 요약합니다.")
    add_summarize_arguments(parser)
    return parser


def main(argv: list[str] | None = None) -> int:
    return run_summarize(build_parser().parse_args(argv))
