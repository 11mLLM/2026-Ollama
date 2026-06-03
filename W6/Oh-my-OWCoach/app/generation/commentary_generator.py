"""EXAONE prompt assembly.

Context is ordered per ARCHITECTURE.md §10.3 so the model prioritizes real
match facts over RAG snippets:

    1. EVENT_TIMELINE      (what actually happened — highest priority)
    2. COMMENTARY_INTENT   (what to talk about)
    3. MATCH_ROSTER        (canonical OWCS players/teams for THIS match)
    4. DO_NOT_SAY          (anti-hallucination guardrails)
    5. RAG_CONTEXT         (terminology / tactics / style — reference only)
"""
from __future__ import annotations

import json

from app.generation.exaone_client import generate_with_exaone
from app.utils.json_utils import parse_json_object


def _fmt(obj) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=2)


def _format_rag_context(rag_context: list[dict]) -> str:
    lines = []
    for doc in rag_context or []:
        meta = doc.get("metadata") or {}
        collection = meta.get("collection", "?")
        text = doc.get("text", "")
        src = meta.get("source_url") or meta.get("source")
        suffix = f"  (출처: {src})" if src else ""
        lines.append(f"- [{collection}] {text}{suffix}")
    return "\n".join(lines) if lines else "(관련 문서 없음)"


def build_commentary_prompt(
    segment: dict,
    plan: dict,
    rag_context: list[dict],
    match_facts: dict | None = None,
) -> str:
    """Build the EXAONE prompt from timeline, intent, roster, and RAG context."""
    do_not_say = plan.get("do_not_say", []) if isinstance(plan, dict) else []
    roster_block = ""
    if match_facts and match_facts.get("context_block"):
        roster_block = (
            "MATCH_ROSTER (이 경기의 실제 OWCS 선수/팀 — 이 명단 밖의 이름을 만들지 마세요):\n"
            f"{match_facts['context_block']}\n"
            f"주의: {match_facts.get('note', '')}\n\n"
        )

    return (
        "EVENT_TIMELINE (실제 발생한 사실 — 최우선 근거):\n"
        f"{_fmt(segment)}\n\n"
        "COMMENTARY_INTENT (무엇을 해설할지):\n"
        f"{_fmt(plan)}\n\n"
        f"{roster_block}"
        "DO_NOT_SAY (환각 방지 금지 사항):\n"
        f"{_fmt(do_not_say)}\n\n"
        "RAG_CONTEXT (용어/전술/문체 참고용 — 새로운 경기 사실로 취급 금지):\n"
        f"{_format_rag_context(rag_context)}\n"
        "\n"
        "Return JSON only using this schema:\n"
        "{\n"
        '  "segment_id": "string",\n'
        '  "start_sec": 0.0,\n'
        '  "end_sec": 0.0,\n'
        '  "style": "string",\n'
        '  "play_by_play": "string",\n'
        '  "color_commentary": "string",\n'
        '  "combined_text": "string",\n'
        '  "facts_used": [],\n'
        '  "uncertain_phrases": [],\n'
        '  "generation_warnings": []\n'
        "}\n"
    )


def generate_commentary_segment(
    segment: dict,
    plan: dict,
    rag_context: list[dict],
    model: str,
) -> tuple[dict, str | None]:
    prompt = build_commentary_prompt(segment, plan, rag_context)
    try:
        raw_output = generate_with_exaone(prompt, model=model)
        parsed = parse_json_object(raw_output)
        return normalize_commentary(parsed, segment, warning=None), None
    except Exception as exc:
        warning = f"EXAONE generation failed: {exc}"
        return fallback_commentary(segment, plan, warning), warning


def normalize_commentary(parsed: dict, segment: dict, warning: str | None) -> dict:
    normalized = {
        "segment_id": parsed.get("segment_id") or segment["segment_id"],
        "start_sec": float(parsed.get("start_sec", segment["start_sec"])),
        "end_sec": float(parsed.get("end_sec", segment["end_sec"])),
        "style": parsed.get("style") or "cautious_observation",
        "play_by_play": parsed.get("play_by_play") or "",
        "color_commentary": parsed.get("color_commentary") or "",
        "combined_text": parsed.get("combined_text") or "",
        "facts_used": parsed.get("facts_used") if isinstance(parsed.get("facts_used"), list) else [],
        "uncertain_phrases": parsed.get("uncertain_phrases")
        if isinstance(parsed.get("uncertain_phrases"), list)
        else [],
        "generation_warnings": parsed.get("generation_warnings")
        if isinstance(parsed.get("generation_warnings"), list)
        else [],
        "needs_human_review": False,
    }
    if not normalized["combined_text"]:
        normalized["combined_text"] = "화면 정보가 부족해 확정적인 중계는 보류합니다."
        normalized["needs_human_review"] = True
    if warning:
        normalized["generation_warnings"].append(warning)
        normalized["needs_human_review"] = True
    return normalized


def fallback_commentary(segment: dict, plan: dict, warning: str) -> dict:
    intent = plan.get("commentary_intent", "uncertain") if isinstance(plan, dict) else "uncertain"
    text = "화면 정보를 확인하는 중입니다. 확실한 장면 근거가 잡히면 교전 흐름을 이어서 설명하겠습니다."
    if intent == "first_pick":
        text = "첫 킬 가능성이 보입니다. 다만 화면 근거가 부족해 확정 표현은 피하겠습니다."
    return {
        "segment_id": segment["segment_id"],
        "start_sec": segment["start_sec"],
        "end_sec": segment["end_sec"],
        "style": "cautious_observation",
        "play_by_play": text,
        "color_commentary": "현재 단계에서는 모델 호출 실패 또는 불확실한 시각 정보로 인해 보수적으로 표현합니다.",
        "combined_text": text,
        "facts_used": [],
        "uncertain_phrases": ["화면 정보를 확인하는 중입니다"],
        "generation_warnings": [warning],
        "needs_human_review": True,
    }
