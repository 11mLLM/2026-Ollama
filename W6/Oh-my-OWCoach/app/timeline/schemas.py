from __future__ import annotations

from dataclasses import asdict, dataclass, field


class Serializable:
    def model_dump(self) -> dict:
        return asdict(self)


@dataclass
class FrameRecord(Serializable):
    frame_id: str
    video_id: str
    time_sec: float
    image_path: str
    roi_paths: dict[str, str] = field(default_factory=dict)
    width: int | None = None
    height: int | None = None


@dataclass
class EventCandidate(Serializable):
    event_type: str
    time_sec: float
    confidence: float = 0.0
    evidence_area: str = "unknown"
    actor: str | None = None
    actor_team: str | None = None
    actor_hero: str | None = None
    target: str | None = None
    target_team: str | None = None
    target_hero: str | None = None
    ability: str | None = None
    source_frame_id: str | None = None
    uncertainty_reason: str | None = None


@dataclass
class SegmentTimeline(Serializable):
    segment_id: str
    video_id: str
    start_sec: float
    end_sec: float
    canonical_events: list[EventCandidate] = field(default_factory=list)
    scene_summary: str = ""
    confidence_overall: float = 0.0
    needs_human_review: bool = False


@dataclass
class CommentarySegment(Serializable):
    segment_id: str
    start_sec: float
    end_sec: float
    style: str
    play_by_play: str
    color_commentary: str
    combined_text: str
    facts_used: list[str] = field(default_factory=list)
    uncertain_phrases: list[str] = field(default_factory=list)
    generation_warnings: list[str] = field(default_factory=list)
    needs_human_review: bool = False
