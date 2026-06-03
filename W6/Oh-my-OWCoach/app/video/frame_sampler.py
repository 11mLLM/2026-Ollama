from __future__ import annotations

from pathlib import Path

from app.video.ingest import import_cv2


def sample_frames(
    video_path: Path,
    output_dir: Path,
    video_id: str,
    sample_fps: float = 1.0,
    max_frames: int = 12,
) -> list[dict]:
    output_dir.mkdir(parents=True, exist_ok=True)
    cv2 = import_cv2()
    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise ValueError(f"Could not open video file: {video_path}")

    source_fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    if source_fps <= 0 or frame_count <= 0:
        capture.release()
        raise ValueError(f"Video has invalid fps/frame_count: {video_path}")

    frame_step = max(1, int(round(source_fps / max(sample_fps, 0.001))))
    frame_indices = list(range(0, frame_count, frame_step))
    if max_frames > 0:
        frame_indices = frame_indices[:max_frames]
    records: list[dict] = []

    for frame_index in frame_indices:
        capture.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
        success, frame = capture.read()
        if not success:
            continue
        time_sec = frame_index / source_fps
        frame_id = f"{video_id}_{time_sec:09.2f}".replace(".", "_")
        image_path = output_dir / f"{time_sec:09.2f}.jpg"
        cv2.imwrite(str(image_path), frame)
        records.append(
            {
                "frame_id": frame_id,
                "video_id": video_id,
                "time_sec": round(time_sec, 3),
                "image_path": str(image_path),
                "roi_paths": {},
                "width": width,
                "height": height,
            }
        )

    capture.release()
    return records
