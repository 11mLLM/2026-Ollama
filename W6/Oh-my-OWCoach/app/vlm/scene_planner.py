"""Agentic scene & commentary-intent planner.

This is the "agent" in the Agentic RAG: given a canonical segment timeline plus
match metadata, it decides (1) the commentary intent/tone, (2) which RAG
**collections** to search, and (3) the **retrieval queries** to run. The Qwen
text model is used when available; a deterministic rule-based planner is the
fallback so the pipeline always produces a usable plan.

Returned plan (CommentaryPlan-compatible)::

    {
      "segment_id": str,
      "commentary_intent": str,
      "tone": str,
      "priority_facts": [str, ...],
      "retrieval_queries": [str, ...],
      "target_collections": [str, ...],   # agentic routing hint for the retriever
      "do_not_say": [str, ...],
      "confidence": float
    }
"""
from __future__ import annotations

from app.llm import LLMUnavailable, chat_text, extract_json

QWEN_VLM_MODEL = "hf.co/jc-builds/Qwen3.5-9B-VLM-Q4_K_M-GGUF:Q4_K_M"

VALID_COLLECTIONS = [
    "hero_abilities",
    "hero_perks",
    "map_strategy",
    "league_info",
    "teams",
    "players",
    "league_terms",
    "tactical_patterns",
    "team_comps",
    "matchups",
    "caster_style_ko",
]

_PLANNER_PROMPT = """당신은 오버워치 챔피언스 시리즈(OWCS) 한국어 중계 플래너입니다.
아래 SEGMENT(정규화된 이벤트 타임라인)와 MATCH_META를 보고, 어떤 해설을 해야 할지와
지식 검색(RAG)을 위해 어떤 컬렉션을 어떤 질의로 찾아야 할지 결정하세요.

절대 규칙:
- SEGMENT에 없는 킬/궁극기/선수명/영웅명/팀명을 새로 만들지 마세요.
- 근거가 약하면 commentary_intent를 "uncertain"으로 두세요.
- target_collections는 다음 중에서만 고르세요: {collections}

JSON만 반환하세요:
{{
  "commentary_intent": "neutral_setup|teamfight_start|first_pick|ultimate_commitment|clutch_play|teamfight_win|objective_progress|reset_or_regroup|uncertain",
  "tone": "energetic_play_by_play|calm_analysis|dramatic_highlight|cautious_observation",
  "priority_facts": ["..."],
  "retrieval_queries": ["검색 질의 1", "검색 질의 2"],
  "target_collections": ["players", "hero_abilities"],
  "do_not_say": ["..."],
  "confidence": 0.0
}}

SEGMENT:
{segment}

MATCH_META:
{match_meta}
"""


def _collect_event_facts(segment: dict) -> tuple[list[str], list[str], list[str], list[str]]:
    """Pull heroes, players, teams, and event types out of a segment."""
    heroes: list[str] = []
    players: list[str] = []
    teams: list[str] = []
    event_types: list[str] = []
    for event in segment.get("canonical_events", []) or []:
        event_types.append(event.get("event_type", "unknown"))
        for key in ("actor_hero", "target_hero"):
            value = event.get(key)
            if value:
                heroes.append(value)
        for key in ("actor", "target"):
            value = event.get(key)
            if value:
                players.append(value)
        for key in ("actor_team", "target_team"):
            value = event.get(key)
            if value and value not in ("blue", "red", "unknown"):
                teams.append(value)
    return (
        list(dict.fromkeys(heroes)),
        list(dict.fromkeys(players)),
        list(dict.fromkeys(teams)),
        list(dict.fromkeys(event_types)),
    )


def _rule_based_plan(segment: dict, match_meta: dict | None = None) -> dict:
    """Deterministic fallback planner with collection routing."""
    match_meta = match_meta or {}
    heroes, players, teams, event_types = _collect_event_facts(segment)
    has_kill = "kill" in event_types
    has_ultimate = "ultimate_used" in event_types
    has_objective = "objective_progress" in event_types

    if has_kill:
        intent, tone = "first_pick", "energetic_play_by_play"
    elif has_ultimate:
        intent, tone = "ultimate_commitment", "dramatic_highlight"
    elif has_objective:
        intent, tone = "objective_progress", "calm_analysis"
    elif event_types:
        intent, tone = "teamfight_start", "energetic_play_by_play"
    else:
        intent, tone = "neutral_setup", "cautious_observation"

    queries: list[str] = []
    collections: list[str] = ["caster_style_ko", "tactical_patterns"]
    for hero in heroes:
        queries.append(f"{hero} 궁극기 스킬 해설")
    if heroes:
        collections.append("hero_abilities")
    for player in players:
        queries.append(f"{player} 선수 소속 팀 OWCS")
    if players or teams:
        collections.append("players")
        collections.append("teams")
    # With 2+ heroes on screen, pull composition/strategy context for comp analysis.
    if len(heroes) >= 2:
        queries.append("팀 조합 전략 다이브 브롤 포크 벙커 상성")
        collections.append("team_comps")
    if match_meta.get("map"):
        queries.append(f"{match_meta['map']} 맵 전략")
        collections.append("map_strategy")
    if intent != "neutral_setup":
        queries.append(f"{intent} 한국어 중계 표현")

    do_not_say = ["EVENT_TIMELINE에 없는 사실을 단정하지 마세요."]
    confidence = float(segment.get("confidence_overall", 0.0) or 0.0)
    low_conf = [
        e for e in (segment.get("canonical_events") or [])
        if (e.get("confidence", 0.0) or 0.0) < 0.5
    ]
    if low_conf:
        do_not_say.append("confidence가 낮은 이벤트는 단정하지 말고 조심스럽게 표현하세요.")
    if has_ultimate and any((e.get("confidence", 0.0) or 0.0) < 0.6 for e in low_conf):
        do_not_say.append("궁극기 사용이 확실하지 않으면 사용했다고 단정하지 마세요.")

    return {
        "segment_id": segment.get("segment_id"),
        "commentary_intent": intent,
        "tone": tone,
        "priority_facts": _build_priority_facts(segment),
        "retrieval_queries": queries or ["오버워치 중계 기본 표현"],
        "target_collections": list(dict.fromkeys(collections)),
        "do_not_say": do_not_say,
        "confidence": confidence,
        "planner": "rule_based",
    }


def _build_priority_facts(segment: dict) -> list[str]:
    facts: list[str] = []
    for event in segment.get("canonical_events", []) or []:
        if event.get("event_type") == "kill" and event.get("actor") and event.get("target"):
            facts.append(f"{event['actor']} eliminated {event['target']}")
        elif event.get("event_type") == "ultimate_used" and event.get("actor"):
            facts.append(f"{event['actor']} used an ultimate")
    return facts


def _sanitize_llm_plan(plan: dict, segment: dict, match_meta: dict | None) -> dict:
    """Keep only valid collections; backfill missing fields from the rule planner."""
    fallback = _rule_based_plan(segment, match_meta)
    collections = [c for c in plan.get("target_collections", []) if c in VALID_COLLECTIONS]
    if not collections:
        collections = fallback["target_collections"]
    queries = [q for q in plan.get("retrieval_queries", []) if isinstance(q, str) and q.strip()]
    if not queries:
        queries = fallback["retrieval_queries"]
    return {
        "segment_id": segment.get("segment_id"),
        "commentary_intent": plan.get("commentary_intent") or fallback["commentary_intent"],
        "tone": plan.get("tone") or fallback["tone"],
        "priority_facts": plan.get("priority_facts") or fallback["priority_facts"],
        "retrieval_queries": queries,
        "target_collections": list(dict.fromkeys(collections)),
        "do_not_say": plan.get("do_not_say") or fallback["do_not_say"],
        "confidence": float(plan.get("confidence", fallback["confidence"]) or 0.0),
        "planner": "qwen",
    }


def plan_commentary(
    segment: dict,
    match_meta: dict | None = None,
    use_llm: bool = True,
    model: str = QWEN_VLM_MODEL,
) -> dict:
    """Create an agentic commentary plan (intent + RAG routing) for a segment.

    Tries the Qwen text planner first; falls back to the deterministic planner
    if the LLM is unavailable or returns unparseable output.
    """
    if use_llm:
        import json as _json

        prompt = _PLANNER_PROMPT.format(
            collections=", ".join(VALID_COLLECTIONS),
            segment=_json.dumps(segment, ensure_ascii=False),
            match_meta=_json.dumps(match_meta or {}, ensure_ascii=False),
        )
        try:
            raw = chat_text(prompt, model=model, temperature=0.0)
            parsed = extract_json(raw)
            if parsed:
                return _sanitize_llm_plan(parsed, segment, match_meta)
        except LLMUnavailable:
            pass
    return _rule_based_plan(segment, match_meta)
