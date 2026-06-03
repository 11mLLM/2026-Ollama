"""Agentic RAG context assembly — the public entrypoint for the RAG subsystem.

Given a canonical segment timeline and match metadata, this:

1. **Plans** (agentic): the Qwen planner decides commentary intent + which RAG
   collections to search + the retrieval queries (``scene_planner``).
2. **Grounds** (anti-hallucination): every VLM-extracted player/hero/team name
   is validated against the KB; misreads get a canonical suggestion and
   unknown names get a "do not assert" warning (``roster``).
3. **Retrieves**: hybrid semantic+keyword search over the routed collections
   (``retriever``).
4. **Assembles** the EXAONE prompt with timeline-first ordering and a canonical
   match roster block (``commentary_generator``).

The pipeline calls :func:`build_rag_context` once per segment.
"""
from __future__ import annotations

from app.generation.commentary_generator import build_commentary_prompt
from app.rag.retriever import AgenticRetriever
from app.rag.roster import RosterIndex
from app.vlm.scene_planner import plan_commentary


def build_rag_context(
    segment: dict,
    match_meta: dict | None = None,
    retriever: AgenticRetriever | None = None,
    roster: RosterIndex | None = None,
    use_llm_planner: bool = True,
    top_k_per_query: int = 3,
    max_docs: int = 8,
) -> dict:
    """Return everything EXAONE needs for one segment, grounded against the KB.

    Returns a dict with keys: ``plan``, ``grounding``, ``match_facts``,
    ``rag_context``, ``prompt``.
    """
    match_meta = match_meta or {}
    retriever = retriever or AgenticRetriever()
    roster = roster or RosterIndex()

    # 1. Agentic plan: intent + collection routing + retrieval queries.
    plan = plan_commentary(segment, match_meta=match_meta, use_llm=use_llm_planner)

    # 2. Ground VLM names; fold grounding warnings into do_not_say.
    grounding = _ground_segment(segment, roster)
    if grounding["warnings"]:
        plan.setdefault("do_not_say", [])
        plan["do_not_say"].extend(grounding["warnings"])

    # 3. Canonical roster facts for THIS match (real OWCS players/teams).
    match_facts = roster.match_facts(match_meta.get("teams"))

    # 4. Retrieve over the routed collections.
    rag_context = retriever.retrieve(
        plan.get("retrieval_queries", []),
        collections=plan.get("target_collections"),
        top_k_per_query=top_k_per_query,
        max_docs=max_docs,
    )

    # 5. Assemble the EXAONE prompt (timeline-first, roster-anchored).
    prompt = build_commentary_prompt(segment, plan, rag_context, match_facts=match_facts)

    return {
        "plan": plan,
        "grounding": grounding,
        "match_facts": match_facts,
        "rag_context": rag_context,
        "prompt": prompt,
    }


def _ground_segment(segment: dict, roster: RosterIndex) -> dict:
    """Validate every name in the segment's events against the KB."""
    per_event: list[dict] = []
    warnings: list[str] = []
    for event in segment.get("canonical_events", []) or []:
        result = roster.ground_event(event)
        per_event.append(result)
        warnings.extend(result["warnings"])
    return {"events": per_event, "warnings": list(dict.fromkeys(warnings))}


if __name__ == "__main__":
    import json

    # Demo segment: a first-pick kill where the VLM MISREAD a player name (L1P)
    # and a hero name, to show the anti-hallucination grounding in action.
    demo_segment = {
        "segment_id": "owl_match_001_0120_0130",
        "start_sec": 120.0,
        "end_sec": 130.0,
        "canonical_events": [
            {
                "event_type": "kill",
                "time_sec": 123.4,
                "actor": "L1P",            # misread of "LIP"
                "actor_team": "blue",
                "actor_hero": "Genji",
                "target": "Fielder",
                "target_team": "red",
                "target_hero": "Ana",
                "confidence": 0.82,
            }
        ],
        "confidence_overall": 0.76,
    }
    demo_meta = {"map": "King's Row", "teams": ["Crazy Raccoon", "Team Falcons"]}

    ctx = build_rag_context(demo_segment, demo_meta, use_llm_planner=False)
    print("PLAN intent:", ctx["plan"]["commentary_intent"], "| collections:", ctx["plan"]["target_collections"])
    print("GROUNDING warnings:", json.dumps(ctx["grounding"]["warnings"], ensure_ascii=False))
    print("RETRIEVED:", [d["id"] for d in ctx["rag_context"]])
    print("\n----- EXAONE PROMPT -----\n")
    print(ctx["prompt"])
