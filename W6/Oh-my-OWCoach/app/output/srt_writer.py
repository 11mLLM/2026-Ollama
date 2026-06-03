from __future__ import annotations

from pathlib import Path


def format_srt_timestamp(seconds: float) -> str:
    millis = int(round(seconds * 1000))
    hours, rem = divmod(millis, 3_600_000)
    minutes, rem = divmod(rem, 60_000)
    secs, ms = divmod(rem, 1000)
    return f"{hours:02}:{minutes:02}:{secs:02},{ms:03}"


def write_srt(path: Path, segments: list[dict]) -> None:
    lines: list[str] = []
    for index, segment in enumerate(segments, start=1):
        start = format_srt_timestamp(float(segment["start_sec"]))
        end = format_srt_timestamp(float(segment["end_sec"]))
        text = segment.get("combined_text", "")
        lines.extend([str(index), f"{start} --> {end}", text, ""])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")

