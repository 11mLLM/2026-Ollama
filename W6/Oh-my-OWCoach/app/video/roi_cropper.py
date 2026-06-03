from __future__ import annotations

from pathlib import Path

from app.video.ingest import import_cv2


def crop_rois(frame_path: Path, output_dir: Path, roi_profile: dict) -> dict[str, str]:
    output_dir.mkdir(parents=True, exist_ok=True)
    cv2 = import_cv2()
    image = cv2.imread(str(frame_path))
    if image is None:
        raise ValueError(f"Could not read frame image: {frame_path}")

    image_height, image_width = image.shape[:2]
    roi_paths: dict[str, str] = {}
    for name, region in roi_profile.get("regions", {}).items():
        x = int(region.get("x", 0))
        y = int(region.get("y", 0))
        w = int(region.get("w", 0))
        h = int(region.get("h", 0))
        x1 = max(0, min(image_width, x))
        y1 = max(0, min(image_height, y))
        x2 = max(x1, min(image_width, x + w))
        y2 = max(y1, min(image_height, y + h))
        if x2 <= x1 or y2 <= y1:
            continue
        crop = image[y1:y2, x1:x2]
        crop_path = output_dir / f"{frame_path.stem}_{name}.jpg"
        cv2.imwrite(str(crop_path), crop)
        roi_paths[name] = str(crop_path)
    return roi_paths


def default_roi_profile() -> dict:
    return {
        "profile_name": "owl_broadcast_default",
        "resolution": [1920, 1080],
        "regions": {
            "top_bar": {"x": 0, "y": 0, "w": 1920, "h": 160},
            "killfeed_right": {"x": 1380, "y": 120, "w": 520, "h": 260},
            "lower_hud": {"x": 0, "y": 740, "w": 1920, "h": 340},
            "center_scene": {"x": 0, "y": 140, "w": 1920, "h": 760},
        },
    }
