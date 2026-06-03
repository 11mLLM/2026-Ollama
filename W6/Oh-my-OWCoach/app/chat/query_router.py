"""Coach query router for OW-MY-COACH.

Analyzes a user's Korean question and decides:
- intent (counter help / comp-by-map / pick-by-role / operation advice / knowledge)
- entities mentioned (heroes, map, role)
- which RAG collections to search and what retrieval queries to run

Rule-based and deterministic: it builds lightweight indexes from the knowledge
base itself (real hero/map/comp names), so entity detection never invents names.
This keeps routing fast and grounded; the EXAONE answer step does the synthesis.
"""
from __future__ import annotations

from app.rag.kb_loader import load_documents

# Role vocabulary -> canonical role tag used in the KB (metadata.role).
_ROLE_TERMS = {
    # '공격'/'수비'는 진영(공격팀/수비팀) 의미와 겹쳐 역할군으로 쓰지 않는다.
    "tank": ["탱커", "탱", "메인탱", "오프탱", "main tank", "off tank", "tank"],
    "dps": ["딜러", "딜", "뎀딜", "히트스캔", "투사체", "플랭커", "원딜", "dps", "damage"],
    "support": ["힐러", "힐", "지원", "서포터", "서폿", "support", "heal"],
}

# Intent trigger keywords (Korean coaching phrasing).
# Counter terms are specific to avoid matching generic verbs like 지다/막다.
_COUNTER_TERMS = [
    "카운터", "상대가", "상대로", "상대법", "힘들", "어려", "터져", "터지", "녹아", "녹여", "녹는",
    "막아", "막혀", "막을", "막는", "이겨", "이길", "못 이기", "못이기", "대응", "처리", "잡는", "잡아",
    "어떻게 막", "어떻게 상대", "counter",
]
_COMP_TERMS = ["조합", "추천", "픽", "뽑", "골라", "comp"]
_OP_TERMS = ["운영", "어떻게", "포지션", "자리", "동선", "한타", "진입", "빠지", "스노우볼", "오브젝트", "점령", "수비", "전략", "운용", "텐션", "템포"]

# Common Korean abbreviations/nicknames -> canonical EN hero name.
_HERO_ALIASES = {
    "위도우": "Widowmaker", "트레": "Tracer", "솔저": "Soldier: 76", "솔져": "Soldier: 76",
    "둠": "Doomfist", "둠피": "Doomfist", "바티": "Baptiste", "브리": "Brigitte",
    "젠야": "Zenyatta", "라인하르트": "Reinhardt", "레낙": "Reinhardt", "윈스": "Winston",
    "정퀸": "Junker Queen", "레킹": "Wrecking Ball", "햄스터": "Wrecking Ball",
    "메이커": "Widowmaker", "캐서": "Cassidy", "맥크리": "Cassidy",
}


def _build_indexes(documents: list[dict]):
    heroes: dict[str, str] = {}  # surface form (lower) -> canonical EN
    maps: dict[str, str] = {}    # surface form (lower) -> canonical map
    comps: list[tuple[str, str]] = []  # (surface, comp_ko)
    for doc in documents:
        meta = doc.get("metadata") or {}
        col = meta.get("collection")
        if col == "hero_abilities" and meta.get("hero"):
            en = meta["hero"]
            heroes[en.lower()] = en
            if meta.get("hero_ko"):
                heroes[meta["hero_ko"].lower()] = en
        elif col == "map_strategy" and meta.get("kind") == "map" and meta.get("map"):
            mp = meta["map"]
            maps[mp.lower()] = mp
            if meta.get("map_ko"):
                maps[meta["map_ko"].lower()] = mp
        elif col == "team_comps" and meta.get("comp_ko"):
            comps.append((meta["comp_ko"], meta["comp_ko"]))
    # Merge common abbreviations only for heroes that exist in the KB.
    canon_set = set(heroes.values())
    for alias, canon in _HERO_ALIASES.items():
        if canon in canon_set:
            heroes.setdefault(alias.lower(), canon)
    return heroes, maps, comps


class CoachQueryRouter:
    def __init__(self, documents: list[dict] | None = None) -> None:
        self.documents = documents if documents is not None else load_documents()
        self.heroes, self.maps, self.comps = _build_indexes(self.documents)

    def _detect_heroes(self, q: str) -> list[str]:
        ql = q.lower()
        found = []
        # Longer surface forms first to avoid partial overshadowing.
        for surface in sorted(self.heroes, key=len, reverse=True):
            if surface and surface in ql:
                canon = self.heroes[surface]
                if canon not in found:
                    found.append(canon)
        return found

    def _detect_map(self, q: str) -> str | None:
        ql = q.lower()
        for surface in sorted(self.maps, key=len, reverse=True):
            if surface and surface in ql:
                return self.maps[surface]
        return None

    def _detect_role(self, q: str) -> str | None:
        ql = q.lower()
        for role, terms in _ROLE_TERMS.items():
            if any(t in ql for t in terms):
                return role
        return None

    @staticmethod
    def _has(q: str, terms: list[str]) -> bool:
        return any(t in q for t in terms)

    def route(
        self,
        query: str,
        user_role: str | None = None,
        user_map: str | None = None,
    ) -> dict:
        heroes = self._detect_heroes(query)
        detected_map = self._detect_map(query) or user_map
        role = self._detect_role(query) or user_role

        is_comp = self._has(query, _COMP_TERMS)
        is_counter = self._has(query, _COUNTER_TERMS)
        is_op = self._has(query, _OP_TERMS)

        # Decide intent (priority order tuned for a coach).
        if is_comp and detected_map:
            intent = "comp_by_map"
        elif is_comp and role:
            intent = "pick_by_role"
        elif is_counter:
            intent = "counter"
        elif is_comp:
            intent = "comp"
        elif is_op:
            intent = "operation"
        else:
            intent = "knowledge"

        collections: list[str] = []
        queries: list[str] = [query]

        def add(*cols):
            for c in cols:
                if c not in collections:
                    collections.append(c)

        # Entity-driven retrieval queries.
        for h in heroes:
            queries.append(f"{h} 영웅 스킬 궁극기 역할")
            add("hero_abilities", "hero_perks")
        if detected_map:
            queries.append(f"{detected_map} 맵 전략")
            add("map_strategy")

        # Intent-driven routing.
        if intent in ("comp_by_map", "comp", "pick_by_role"):
            add("team_comps", "hero_abilities", "map_strategy")
            queries.append("팀 조합 추천 다이브 브롤 포크 벙커 상성")
            if role:
                queries.append(f"{role} 역할군 픽 추천")
        if intent == "counter":
            add("matchups", "hero_abilities", "team_comps", "league_terms")
            # Per-hero counter queries hit the matchups docs directly.
            for h in heroes:
                queries.append(f"{h} 상성 카운터 약점 대응")
            if not heroes:
                queries.append("카운터 상성 대응 약점")
        if intent == "operation":
            add("team_comps", "tactical_patterns", "league_terms", "map_strategy", "matchups", "hero_abilities")
            queries.append("운영 한타 포지셔닝 궁극기 템포")
            for h in heroes:
                queries.append(f"{h} 상성 카운터 대응")
        if intent == "knowledge":
            # broad: let retriever rank across the main factual collections
            add(
                "hero_abilities", "hero_perks", "matchups", "map_strategy", "team_comps",
                "league_terms", "league_info", "teams", "players",
            )

        return {
            "intent": intent,
            "heroes": heroes,
            "map": detected_map,
            "role": role,
            "target_collections": collections,
            "retrieval_queries": list(dict.fromkeys(queries)),
        }


_ROLE_KO = {"tank": "돌격(탱커)", "dps": "공격(딜러)", "support": "지원(힐러)"}


def role_label(role: str | None) -> str:
    return _ROLE_KO.get(role or "", "미지정")
