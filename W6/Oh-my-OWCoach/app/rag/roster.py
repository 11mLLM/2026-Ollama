"""Roster & name grounding for anti-hallucination.

The RAG knowledge base does more than feed style snippets to EXAONE: it is the
ground-truth dictionary the pipeline checks VLM-extracted names against. This
module turns the ``players``, ``teams``, and ``hero_abilities`` collections into
fast lookup tables and exposes:

- ``validate_player`` / ``validate_team`` / ``validate_hero`` — does this name
  exist in the KB? Returns a canonical match (with close-match suggestion).
- ``ground_event`` — correct/flag the names inside one VLM event.
- ``match_facts`` — build a canonical roster context block for a given match so
  EXAONE anchors on real OWCS players instead of inventing them.

Everything is snapshot-aware: facts carry the ``snapshot`` (e.g.
``2025_world_finals``) and ``source_url`` so commentary never asserts a roster
as "current" when it is a dated record.
"""
from __future__ import annotations

import difflib

from app.rag.kb_loader import load_documents

# Fuzzy-match threshold for "did the VLM misread this name" suggestions.
_CLOSE_MATCH_CUTOFF = 0.8

# Visually-confusable characters common in HUD/killfeed misreads.
# Note: I / l / 1 collapse to a single token because they are mutually ambiguous.
_CONFUSABLES = str.maketrans(
    {"0": "o", "1": "i", "l": "i", "3": "e", "5": "s", "7": "t", "8": "b"}
)


def _normalize(name: str) -> str:
    """Lowercase + collapse visually-confusable digits so 'L1P' ~ 'LIP'."""
    return name.lower().translate(_CONFUSABLES)


class RosterIndex:
    def __init__(self, documents: list[dict] | None = None) -> None:
        docs = documents if documents is not None else load_documents()
        self.players: dict[str, dict] = {}
        self.teams: dict[str, dict] = {}
        self.team_aliases: dict[str, str] = {}  # ko / lower -> canonical team
        self.heroes: dict[str, dict] = {}
        self.hero_aliases: dict[str, str] = {}  # ko / lower -> canonical hero
        self._ingest(docs)

    def _ingest(self, docs: list[dict]) -> None:
        for doc in docs:
            meta = doc.get("metadata") or {}
            collection = meta.get("collection")
            if collection == "players" and meta.get("player"):
                self.players[meta["player"].lower()] = meta
            elif collection == "teams" and meta.get("team"):
                self.teams[meta["team"].lower()] = meta
                if meta.get("team_ko"):
                    self.team_aliases[meta["team_ko"]] = meta["team"]
            elif collection == "hero_abilities" and meta.get("hero"):
                self.heroes[meta["hero"].lower()] = meta
                if meta.get("hero_ko"):
                    self.hero_aliases[meta["hero_ko"]] = meta["hero"]

    # -- generic validation ------------------------------------------------- #
    @staticmethod
    def _validate(
        name: str | None,
        table: dict[str, dict],
        aliases: dict[str, str],
        canonical_key: str,
    ) -> dict:
        if not name:
            return {"input": name, "valid": False, "canonical": None, "suggestion": None}
        # exact alias (Korean display name) hit
        if name in aliases:
            canonical = aliases[name]
            return {"input": name, "valid": True, "canonical": canonical, "suggestion": None}
        meta = table.get(name.lower())
        if meta:
            return {"input": name, "valid": True, "canonical": meta.get(canonical_key), "suggestion": None}
        # fuzzy: maybe the VLM misread the on-screen text (incl. digit/letter swaps)
        norm_to_canonical: dict[str, str] = {}
        for key, meta in table.items():
            norm_to_canonical[_normalize(key)] = meta.get(canonical_key)
        for ko, canon in aliases.items():
            norm_to_canonical[_normalize(ko)] = canon
        close = difflib.get_close_matches(
            _normalize(name), list(norm_to_canonical.keys()), n=1, cutoff=_CLOSE_MATCH_CUTOFF
        )
        suggestion = norm_to_canonical.get(close[0]) if close else None
        return {"input": name, "valid": False, "canonical": None, "suggestion": suggestion}

    def validate_player(self, name: str | None) -> dict:
        return self._validate(name, self.players, {}, "player")

    def validate_team(self, name: str | None) -> dict:
        return self._validate(name, self.teams, self.team_aliases, "team")

    def validate_hero(self, name: str | None) -> dict:
        return self._validate(name, self.heroes, self.hero_aliases, "hero")

    # -- event-level grounding ---------------------------------------------- #
    def ground_event(self, event: dict) -> dict:
        """Return grounding annotations for one VLM event (non-destructive)."""
        annotations: dict[str, dict] = {}
        warnings: list[str] = []
        for field, validator in (
            ("actor", self.validate_player),
            ("target", self.validate_player),
            ("actor_hero", self.validate_hero),
            ("target_hero", self.validate_hero),
        ):
            value = event.get(field)
            if not value:
                continue
            result = validator(value)
            annotations[field] = result
            if not result["valid"]:
                if result["suggestion"]:
                    warnings.append(
                        f"{field}='{value}' KB에 없음, '{result['suggestion']}' 오독 가능성"
                    )
                else:
                    warnings.append(f"{field}='{value}' KB 미등록 — 단정 금지")
        return {"annotations": annotations, "warnings": warnings}

    # -- match-level context ------------------------------------------------ #
    def match_facts(self, teams: list[str] | None) -> dict:
        """Build a canonical roster context block for the given match teams."""
        teams = teams or []
        resolved: list[dict] = []
        roster_lines: list[str] = []
        for team in teams:
            team_meta = self.teams.get(team.lower()) or {
                "team": self.team_aliases.get(team)
            }
            canonical = team_meta.get("team") if team_meta else None
            if not canonical:
                continue
            members = [
                m for m in self.players.values() if (m.get("team") or "").lower() == canonical.lower()
            ]
            resolved.append({"team": canonical, "players": members})
            if members:
                names = ", ".join(f"{m['player']}({m.get('role')})" for m in members)
                snap = members[0].get("snapshot", "")
                roster_lines.append(f"{canonical}: {names} [{snap}]")
        return {
            "teams": [r["team"] for r in resolved],
            "rosters": resolved,
            "context_block": "\n".join(roster_lines),
            "note": "스냅샷 기준 로스터입니다. 현재 라인업과 다를 수 있으므로 '현재'라고 단정하지 마세요.",
        }


def get_roster_index() -> RosterIndex:
    return RosterIndex()


if __name__ == "__main__":
    import json

    idx = get_roster_index()
    print("players:", len(idx.players), "teams:", len(idx.teams), "heroes:", len(idx.heroes))
    print(json.dumps(idx.validate_player("LIP"), ensure_ascii=False))
    print(json.dumps(idx.validate_player("L1P"), ensure_ascii=False))  # misread -> suggestion
    print(json.dumps(idx.validate_hero("겐지"), ensure_ascii=False))
    print(idx.match_facts(["Crazy Raccoon", "Team Falcons"])["context_block"])
