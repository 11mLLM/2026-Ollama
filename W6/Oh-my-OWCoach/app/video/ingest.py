from __future__ import annotations

from pathlib import Path


def read_video_metadata(video_path: Path) -> dict:
    cv2 = import_cv2()
    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise ValueError(f"Could not open video file: {video_path}")

    fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    duration_sec = frame_count / fps if fps > 0 else 0.0
    capture.release()

    return {
        "video_path": str(video_path),
        "video_id": video_path.stem,
        "fps": fps,
        "duration_sec": duration_sec,
        "width": width,
        "height": height,
        "frame_count": frame_count,
    }


def import_cv2():
    try:
        import cv2
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "OpenCV is required for video processing. Install dependencies with "
            "`pip install -r requirements.txt`."
        ) from exc
    return cv2
