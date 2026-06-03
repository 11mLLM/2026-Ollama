from __future__ import annotations

from pathlib import Path


def write_run_report(path: Path, report: dict) -> None:
    lines = ["# Run Report", ""]
    for key, value in report.items():
        if isinstance(value, list):
            lines.append(f"## {key}")
            lines.extend(f"- {item}" for item in value)
            lines.append("")
        else:
            lines.append(f"- **{key}:** {value}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")

