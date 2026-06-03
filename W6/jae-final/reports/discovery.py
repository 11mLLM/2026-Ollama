"""Report discovery, grouping, and document loading."""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any


TOOL_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROJECT_DIR = Path(os.environ.get("HOME_SEARCH_PROJECT_DIR", "/Users/gwongwangjae/home-search")).expanduser()
DEFAULT_PROJECT_ROOT = DEFAULT_PROJECT_DIR.resolve() if DEFAULT_PROJECT_DIR.exists() else TOOL_ROOT
DEFAULT_RECENT_GROUPS = 5
REPORT_EXTENSIONS = {".md", ".txt", ".json"}
GROUP_SUFFIXES = (
    "-pr-body",
    "-backend-gate",
    "-backend-last",
    "-frontend-gate",
    "-frontend-last",
)
JSON_SUMMARY_KEYS = (
    "status",
    "preset",
    "targets",
    "started_at",
    "finished_at",
    "branches",
    "verification",
    "contract_risks",
    "residual_risks",
    "next_action",
)


class ReportError(RuntimeError):
    """Raised when local report input cannot be read."""


@dataclass(frozen=True)
class ReportGroup:
    slug: str
    files: tuple[Path, ...]
    modified_at: datetime


@dataclass(frozen=True)
class Selection:
    project_root: Path
    reports_dir: Path
    groups: tuple[ReportGroup, ...]
    reason: str


def rel(path: Path, project_root: Path = DEFAULT_PROJECT_ROOT) -> str:
    try:
        return path.resolve().relative_to(project_root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def positive_int(value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be an integer") from exc
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than 0")
    return parsed


def parse_iso_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must use YYYY-MM-DD") from exc


def resolve_project_root(value: Path | str | None) -> Path:
    if value is None:
        return DEFAULT_PROJECT_ROOT
    return Path(value).expanduser().resolve()


def local_mtime(path: Path) -> datetime:
    return datetime.fromtimestamp(path.stat().st_mtime).astimezone()


def canonical_slug(path: Path) -> str:
    slug = path.stem
    for suffix in GROUP_SUFFIXES:
        if slug.endswith(suffix):
            stripped = slug[: -len(suffix)]
            return stripped or slug
    return slug


def report_role(path: Path, slug: str) -> str:
    stem = path.stem
    if stem == slug:
        if path.suffix == ".json":
            return "payload-json"
        return "main"
    for suffix in GROUP_SUFFIXES:
        if stem == f"{slug}{suffix}":
            return suffix.removeprefix("-")
    return "other"


def role_order(path: Path, slug: str) -> tuple[int, str]:
    order = {
        "main": 0,
        "payload-json": 1,
        "backend-gate": 2,
        "frontend-gate": 3,
        "backend-last": 4,
        "frontend-last": 5,
        "pr-body": 6,
        "other": 7,
    }
    return (order.get(report_role(path, slug), 99), path.name)


def eligible_report_files(reports_dir: Path) -> list[Path]:
    if not reports_dir.exists() or not reports_dir.is_dir():
        return []
    return sorted(
        path
        for path in reports_dir.iterdir()
        if path.is_file()
        and not path.name.startswith(".")
        and path.suffix.lower() in REPORT_EXTENSIONS
    )


def choose_reports_dir(explicit: Path | None, project_root: Path) -> Path:
    if explicit is not None:
        return explicit.expanduser().resolve()

    candidates = (
        project_root / ".codex" / "reports",
        project_root / ".codex" / "harness" / "reports",
    )
    for candidate in candidates:
        if eligible_report_files(candidate):
            return candidate
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[0]


def group_report_files(files: list[Path]) -> tuple[ReportGroup, ...]:
    grouped: dict[str, list[Path]] = {}
    for path in files:
        grouped.setdefault(canonical_slug(path), []).append(path)

    groups: list[ReportGroup] = []
    for slug, paths in grouped.items():
        ordered_paths = tuple(sorted(paths, key=lambda item: role_order(item, slug)))
        modified_at = max(local_mtime(path) for path in ordered_paths)
        groups.append(ReportGroup(slug=slug, files=ordered_paths, modified_at=modified_at))
    return tuple(sorted(groups, key=lambda item: (item.modified_at, item.slug), reverse=True))


def select_groups(
    project_root: Path,
    reports_dir: Path,
    *,
    selected_date: date | None,
    recent: int | None,
) -> Selection:
    groups = group_report_files(eligible_report_files(reports_dir))
    if recent is not None:
        selected = groups[:recent]
        return Selection(project_root, reports_dir, selected, f"recent {recent} report groups")

    if selected_date is not None:
        selected = tuple(group for group in groups if group.modified_at.date() == selected_date)
        return Selection(project_root, reports_dir, selected, f"mtime date {selected_date.isoformat()}")

    today = date.today()
    selected = tuple(group for group in groups if group.modified_at.date() == today)
    if selected:
        return Selection(project_root, reports_dir, selected, f"today {today.isoformat()}")
    selected = groups[:DEFAULT_RECENT_GROUPS]
    return Selection(
        project_root,
        reports_dir,
        selected,
        f"today {today.isoformat()} had no reports; recent {DEFAULT_RECENT_GROUPS} report groups",
    )


def compact_json_payload(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        return {"json_root": type(payload).__name__, "value": payload}

    compact: dict[str, Any] = {}
    for key in JSON_SUMMARY_KEYS:
        if key in payload:
            compact[key] = payload[key]

    links = payload.get("links")
    if isinstance(links, dict):
        pr_url = links.get("pr_url")
        if pr_url:
            compact["pr_url"] = pr_url

    return compact


def read_raw_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ReportError(f"파일을 읽을 수 없습니다: {path}: {exc}") from exc


def read_report_document(path: Path) -> str:
    text = read_raw_text(path)
    if path.suffix.lower() != ".json":
        return text.strip()

    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ReportError(f"JSON 보고서를 파싱할 수 없습니다: {path}: {exc}") from exc
    return json.dumps(compact_json_payload(payload), ensure_ascii=False, indent=2, sort_keys=True)


def _metadata_from_json(path: Path) -> dict[str, str]:
    if path.suffix.lower() != ".json":
        return {}
    try:
        payload = json.loads(read_raw_text(path))
    except (ReportError, json.JSONDecodeError):
        return {}
    if not isinstance(payload, dict):
        return {}
    metadata: dict[str, str] = {}
    for key in ("status", "preset", "targets"):
        value = payload.get(key)
        if value is not None:
            metadata[key] = str(value)
    return metadata


def _metadata_from_text(path: Path) -> dict[str, str]:
    if path.suffix.lower() == ".json":
        return {}
    metadata: dict[str, str] = {}
    text = read_raw_text(path)
    patterns = {
        "status": re.compile(r"^\s*(?:상태|status)\s*[:：]\s*(?P<value>.+?)\s*$", re.IGNORECASE),
        "preset": re.compile(r"^\s*preset\s*[:：]\s*(?P<value>.+?)\s*$", re.IGNORECASE),
        "targets": re.compile(r"^\s*targets\s*[:：]\s*(?P<value>.+?)\s*$", re.IGNORECASE),
    }
    for line in text.splitlines():
        stripped = line.strip("- ")
        for key, pattern in patterns.items():
            if key in metadata:
                continue
            match = pattern.match(stripped)
            if match:
                metadata[key] = match.group("value").strip("` ")
    return metadata


def report_file_metadata(path: Path, project_root: Path, slug: str) -> dict[str, str | int]:
    text_metadata = _metadata_from_json(path) or _metadata_from_text(path)
    metadata: dict[str, str | int] = {
        "project_dir": str(project_root),
        "report_path": rel(path, project_root),
        "report_slug": slug,
        "file_role": report_role(path, slug),
        "modified_at": local_mtime(path).isoformat(timespec="seconds"),
        "source_ext": path.suffix.lower().lstrip("."),
        "file_mtime_ns": path.stat().st_mtime_ns,
    }
    for key in ("status", "preset", "targets"):
        value = text_metadata.get(key)
        if value:
            metadata[key] = value
    return metadata


def all_report_groups(project_root: Path, reports_dir: Path) -> tuple[ReportGroup, ...]:
    return group_report_files(eligible_report_files(reports_dir))

