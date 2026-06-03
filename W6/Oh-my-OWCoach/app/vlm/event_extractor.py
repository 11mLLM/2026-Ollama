from __future__ import annotations

from pathlib import Path

from app.utils.json_utils import ensure_list, parse_json_object
from app.vlm.qwen_client import analyze_frame_with_qwen_vlm


PROMPT_PATH = Path("app/config/prompts/qwen_vlm_event_extraction.md")
COMPACT_VISION_PROMPT = (
    "Read this Overwatch HUD crop. Return JSON only with keys scene_summary, events, warnings."
)


def extract_event_candidates(
    frame_records: list[dict],
    model: str,
    max_model_frames: int = 6,
    frame_stride: int = 10,
) -> tuple[list[dict], list[dict], list[str]]:
    selected_frames = frame_records[:: max(1, frame_stride)][:max_model_frames]
    candidates: list[dict] = []
    frame_outputs: list[dict] = []
    warnings: list[str] = []

    for frame in selected_frames:
        prompt = build_frame_prompt(frame)
        image_path = choose_vlm_image_path(frame)
        try:
            raw_output = analyze_frame_with_qwen_vlm(prompt, image_path, model=model)
            parsed = parse_json_object(raw_output)
        except Exception as exc:
            warning = f"Qwen VLM failed for {frame['frame_id']}: {exc}"
            warnings.append(warning)
            parsed = fallback_frame_output(frame, warning)
            raw_output = ""
            if is_non_recoverable_vlm_error(str(exc)):
                skipped = len(selected_frames) - len(frame_outputs) - 1
                if skipped > 0:
                    warnings.append(
                        f"Skipped {skipped} remaining VLM frames because the model/runtime error is non-recoverable."
                    )
                events = []
                parsed["events"] = events
                frame_outputs.append(
                    {
                        "frame_id": frame["frame_id"],
                        "time_sec": frame["time_sec"],
                        "image_path": frame["image_path"],
                        "raw_model_output": raw_output,
                        "parsed_output": parsed,
                    }
                )
                break

        events = []
        for event in ensure_list(parsed.get("events")):
            if not isinstance(event, dict):
                continue
            event.setdefault("source_frame_id", frame["frame_id"])
            event.setdefault("time_sec", frame["time_sec"])
            event.setdefault("confidence", 0.0)
            event.setdefault("evidence_area", "unknown")
            candidates.append(event)
            events.append(event)

        parsed["events"] = events
        frame_outputs.append(
            {
                "frame_id": frame["frame_id"],
                "time_sec": frame["time_sec"],
                "image_path": frame["image_path"],
                "source_image_path": str(image_path),
                "raw_model_output": raw_output,
                "parsed_output": parsed,
            }
        )

    return candidates, frame_outputs, warnings


def is_non_recoverable_vlm_error(error_text: str) -> bool:
    lowered = error_text.lower()
    return (
        "unable to load model" in lowered
        or "model does not support images" in lowered
        or "unsupported image" in lowered
    )


def build_frame_prompt(frame: dict) -> str:
    selected_image = choose_vlm_image_path(frame)
    return (
        f"{COMPACT_VISION_PROMPT}\n"
        f"frame_id={frame['frame_id']}; time_sec={frame['time_sec']}"
    )


def choose_vlm_image_path(frame: dict) -> Path:
    roi_paths = frame.get("roi_paths") or {}
    for key in ("killfeed_right", "top_bar", "lower_hud", "center_scene"):
        candidate = roi_paths.get(key)
        if candidate:
            return Path(candidate)
    return Path(frame["image_path"])


def fallback_frame_output(frame: dict, warning: str) -> dict:
    return {
        "frame_id": frame["frame_id"],
        "time_sec": frame["time_sec"],
        "camera_view": "unknown",
        "visible_hud": {
            "killfeed_present": False,
            "scoreboard_present": False,
            "objective_ui_present": False,
            "broadcast_overlay_present": False,
        },
        "events": [],
        "scene_summary": "VLM extraction unavailable for this frame.",
        "commentary_intent": {
            "segment_type": "uncertain",
            "recommended_tone": "cautious_observation",
            "key_points": [],
        },
        "warnings": [warning],
        "needs_human_review": True,
    }
