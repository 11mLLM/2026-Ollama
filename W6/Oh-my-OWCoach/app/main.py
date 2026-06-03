from __future__ import annotations

import argparse
from pathlib import Path

from app.config.loader import get_nested, load_pipeline_config
from app.generation.commentary_generator import generate_commentary_segment
from app.output.json_writer import write_json
from app.output.report_writer import write_run_report
from app.rag.retriever import load_knowledge_base, retrieve_context
from app.timeline.schemas import CommentarySegment, FrameRecord, SegmentTimeline
from app.video.frame_sampler import sample_frames
from app.video.ingest import read_video_metadata
from app.video.roi_cropper import crop_rois, default_roi_profile
from app.vlm.event_extractor import extract_event_candidates
from app.vlm.scene_planner import plan_commentary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the Vision-to-Caster MVP pipeline.")
    parser.add_argument("--video", default="input/videos/demo.mkv", help="Path to the input video file.")
    parser.add_argument("--video-id", default=None, help="Optional stable video id.")
    parser.add_argument("--max-frames", type=int, default=None, help="Maximum frames to sample.")
    parser.add_argument("--sample-fps", type=float, default=None, help="Target sampling rate.")
    parser.add_argument("--model-frame-limit", type=int, default=None, help="Maximum frames sent to VLM.")
    parser.add_argument("--model-frame-stride", type=int, default=None, help="Stride over sampled frames for VLM.")
    parser.add_argument("--config", default="app/config/pipeline.yaml", help="Path to pipeline config.")
    parser.add_argument("--dry-run", action="store_true", help="Create run report without model calls.")
    return parser.parse_args()


def build_contract_samples(video_id: str, video_path: Path) -> dict:
    frame = FrameRecord(
        frame_id=f"{video_id}_000000_00",
        video_id=video_id,
        time_sec=0.0,
        image_path=f"data/frames/{video_id}/000000.00.jpg",
        roi_paths={},
        width=None,
        height=None,
    )
    timeline = SegmentTimeline(
        segment_id=f"{video_id}_0000_0010",
        video_id=video_id,
        start_sec=0.0,
        end_sec=10.0,
        canonical_events=[],
        scene_summary="Dry-run contract sample.",
        confidence_overall=0.0,
        needs_human_review=True,
    )
    commentary = CommentarySegment(
        segment_id=timeline.segment_id,
        start_sec=timeline.start_sec,
        end_sec=timeline.end_sec,
        style="cautious_observation",
        play_by_play="Dry-run placeholder.",
        color_commentary="Dry-run placeholder.",
        combined_text="Dry-run placeholder.",
        facts_used=[],
        uncertain_phrases=[],
        generation_warnings=["No model calls were made."],
        needs_human_review=True,
    )
    return {
        "video_path": str(video_path),
        "frame_record": frame.model_dump(),
        "segment_timeline": timeline.model_dump(),
        "commentary_segment": commentary.model_dump(),
    }


def main() -> int:
    args = parse_args()
    config = load_pipeline_config(Path(args.config))
    video_path = Path(args.video)
    output_dir = Path(get_nested(config, "paths.output", "output"))
    output_dir.mkdir(parents=True, exist_ok=True)

    video_id = args.video_id or video_path.stem
    max_frames = args.max_frames if args.max_frames is not None else int(get_nested(config, "runtime.max_frames", 0))
    sample_fps = args.sample_fps if args.sample_fps is not None else float(get_nested(config, "runtime.sample_fps", 1.0))
    model_frame_limit = (
        args.model_frame_limit
        if args.model_frame_limit is not None
        else int(get_nested(config, "runtime.model_frame_limit", 6))
    )
    model_frame_stride = (
        args.model_frame_stride
        if args.model_frame_stride is not None
        else int(get_nested(config, "runtime.model_frame_stride", 10))
    )
    contract_samples = build_contract_samples(video_id, video_path)
    metadata: dict | None = None
    frame_records: list[dict] = []
    event_candidates: list[dict] = []
    qwen_frame_outputs: list[dict] = []
    commentary_segments: list[dict] = []
    processing_errors: list[str] = []

    report = {
        "status": "dry_run" if args.dry_run else "not_implemented",
        "video_id": video_id,
        "video_path": str(video_path),
        "config_path": args.config,
        "qwen_vlm_model": get_nested(config, "models.qwen_vlm"),
        "exaone_model": get_nested(config, "models.exaone"),
        "max_frames": max_frames,
        "sample_fps": sample_fps,
        "model_frame_limit": model_frame_limit,
        "model_frame_stride": model_frame_stride,
        "contract_validation": "passed",
        "frame_count_sampled": 0,
        "roi_crop_count": 0,
        "event_candidate_count": 0,
        "commentary_segment_count": 0,
        "notes": [
            "Phase 1 CLI skeleton and data contracts are initialized.",
            "Phase 2 video ingest, frame sampling, and ROI crop pipeline is initialized.",
        ],
    }

    if not video_path.exists():
        report["status"] = "missing_input"
        report["notes"].append("Expected input video at the provided --video path.")
    else:
        try:
            metadata = read_video_metadata(video_path)
            frame_output_dir = Path(get_nested(config, "paths.frames", "data/frames")) / video_id
            crop_output_dir = Path(get_nested(config, "paths.crops", "data/crops")) / video_id
            frame_records = sample_frames(
                video_path=video_path,
                output_dir=frame_output_dir,
                video_id=video_id,
                sample_fps=sample_fps,
                max_frames=max_frames,
            )
            roi_profile = default_roi_profile()
            for record in frame_records:
                try:
                    record["roi_paths"] = crop_rois(
                        frame_path=Path(record["image_path"]),
                        output_dir=crop_output_dir,
                        roi_profile=roi_profile,
                    )
                except Exception as exc:  # Keep full-frame path usable.
                    processing_errors.append(f"ROI crop failed for {record['frame_id']}: {exc}")
                    record["roi_paths"] = {}

            report["status"] = "video_processed_dry_run" if args.dry_run else "video_processed"
            report["video_metadata"] = metadata
            report["frame_count_sampled"] = len(frame_records)
            report["roi_crop_count"] = sum(len(record.get("roi_paths", {})) for record in frame_records)
            report["notes"].append("Frame manifest was generated.")
            write_json(frame_output_dir / "frame_manifest.json", frame_records)

            if not args.dry_run:
                event_candidates, qwen_frame_outputs, qwen_warnings = extract_event_candidates(
                    frame_records=frame_records,
                    model=get_nested(config, "models.qwen_vlm"),
                    max_model_frames=model_frame_limit,
                    frame_stride=model_frame_stride,
                )
                processing_errors.extend(qwen_warnings)
                segment = build_single_segment(video_id, frame_records, event_candidates)
                plan = plan_commentary(
                    segment,
                    match_meta={"video_id": video_id, "video_path": str(video_path)},
                    use_llm=False,
                )
                rag_docs = load_knowledge_base(Path("app/rag/knowledge_base.jsonl"))
                rag_context = retrieve_context(plan.get("retrieval_queries", []), rag_docs, top_k=5)
                commentary, exaone_warning = generate_commentary_segment(
                    segment=segment,
                    plan=plan,
                    rag_context=rag_context,
                    model=get_nested(config, "models.exaone"),
                )
                if exaone_warning:
                    processing_errors.append(exaone_warning)
                commentary_segments = [commentary]
                report["event_candidate_count"] = len(event_candidates)
                report["commentary_segment_count"] = len(commentary_segments)
                report["notes"].append("Phase 3 model integration path was executed.")
        except Exception as exc:
            report["status"] = "video_processing_failed"
            processing_errors.append(str(exc))

    if processing_errors:
        report["processing_errors"] = processing_errors

    write_json(output_dir / "dry_run_contracts.json", contract_samples)
    write_json(output_dir / "frame_manifest.json", frame_records)
    write_json(output_dir / "event_candidates.json", event_candidates)
    write_json(output_dir / "qwen_frame_outputs.json", qwen_frame_outputs)
    write_json(
        output_dir / "commentary.json",
        {
            "video_id": video_id,
            "model_versions": {
                "vlm": get_nested(config, "models.qwen_vlm"),
                "generator": get_nested(config, "models.exaone"),
            },
            "segments": commentary_segments,
        },
    )
    write_run_report(output_dir / "run_report.md", report)
    print(f"Run report written to {output_dir / 'run_report.md'}")
    print(f"Contract samples written to {output_dir / 'dry_run_contracts.json'}")
    print(f"Frame manifest written to {output_dir / 'frame_manifest.json'}")
    print(f"Event candidates written to {output_dir / 'event_candidates.json'}")
    print(f"Commentary written to {output_dir / 'commentary.json'}")
    return 0


def build_single_segment(video_id: str, frame_records: list[dict], event_candidates: list[dict]) -> dict:
    if frame_records:
        start_sec = float(frame_records[0]["time_sec"])
        end_sec = float(frame_records[-1]["time_sec"])
    else:
        start_sec = 0.0
        end_sec = 0.0
    return {
        "segment_id": f"{video_id}_{start_sec:04.0f}_{end_sec:04.0f}",
        "video_id": video_id,
        "start_sec": start_sec,
        "end_sec": end_sec,
        "canonical_events": event_candidates,
        "scene_summary": "Initial Phase 3 segment built from sampled frames.",
        "confidence_overall": max((float(event.get("confidence", 0.0)) for event in event_candidates), default=0.0),
        "needs_human_review": not event_candidates,
    }


if __name__ == "__main__":
    raise SystemExit(main())
