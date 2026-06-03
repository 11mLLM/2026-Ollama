"""OW-MY-COACH — Overwatch competitive coaching chatbot.

Ties together the query router, the Agentic RAG retriever, and EXAONE:

    user question
      -> CoachQueryRouter (intent + entities + collection routing)
      -> AgenticRetriever (hybrid RAG over routed collections)
      -> grounded coaching prompt (persona + KNOWLEDGE + role/map)
      -> EXAONE answer (Korean), with sources

The answer is grounded strictly in retrieved KB documents to avoid
hallucinating heroes, abilities, perks, or rosters.
"""
from __future__ import annotations

from app.chat.query_router import CoachQueryRouter, role_label
from app.generation.exaone_client import EXAONE_MODEL, chat_with_exaone
from app.llm import LLMUnavailable
from app.rag.retriever import AgenticRetriever

SYSTEM_PROMPT = """너는 'OW-MY-COACH', 오버워치 2 경쟁전 전문 코치 챗봇이다.
사용자의 문제 상황을 듣고, 역할군과 맵을 고려해 픽과 운영을 코칭한다.

[근거 사용 — 환각 금지: 매우 중요]
- 아래 KNOWLEDGE 블록에 적힌 내용만 사실 근거로 사용한다. 이것이 너의 유일한 지식 출처다.
- 특히 영웅 상성/카운터/궁합은 KNOWLEDGE의 '상성·궁합'(matchups) 항목에 적힌 영웅만 근거로 말한다.
  KNOWLEDGE에 그 영웅의 상성 정보가 없으면, 카운터를 지어내지 말고 "정확한 상성 정보가 없다"고 말한 뒤
  일반 원칙(예: 다이브는 군중 제어·보호로 받아친다)만 조심스럽게 제시한다.
- KNOWLEDGE에 없는 영웅·스킬·궁극기·특전·선수·팀·수치는 절대 만들어내지 않는다.
- 확신이 없으면 단정하지 말고 모른다고 솔직히 말한다. 틀린 단정보다 "모른다"가 낫다.
- 특전(Perks)·OWCS 로스터는 시즌마다 바뀌므로 '스냅샷 기준'임을 덧붙인다.

[답변 형식]
- 먼저 질문 의도를 한 문장으로 짚고 시작한다(예: "겐지 카운터 질문이군요").
- 카운터/문제 상황: (원인 → 추천 픽/행동 → 운영 팁) 순서. 추천 픽은 KNOWLEDGE 근거가 있는 영웅만.
- 조합/픽 추천: 사용자 ROLE에 맞춰 (추천 픽 → 이유 → 운영 팁 → 주의할 카운터).
- 한국어로 간결하고 실전적으로. 핵심부터. 불필요한 장황함 금지.
- 질문과 무관한 내용을 덧붙이지 않는다.
"""


def _format_knowledge(docs: list[dict]) -> str:
    if not docs:
        return "(검색된 근거 없음 — 일반 원칙으로만 신중히 답하거나 모른다고 말할 것)"
    lines = []
    for d in docs:
        meta = d.get("metadata") or {}
        col = meta.get("collection", "?")
        lines.append(f"- [{col}] {d.get('text','')}")
    return "\n".join(lines)


def _collect_sources(docs: list[dict]) -> list[str]:
    seen = []
    for d in docs:
        meta = d.get("metadata") or {}
        src = meta.get("source_url") or meta.get("source_ref") or meta.get("source")
        if src and src not in seen:
            seen.append(src)
    return seen


class Coach:
    def __init__(self, model: str = EXAONE_MODEL) -> None:
        self.model = model
        self.retriever = AgenticRetriever()
        self.router = CoachQueryRouter(self.retriever.documents)

    def ask(
        self,
        query: str,
        history: list[dict] | None = None,
        user_role: str | None = None,
        user_map: str | None = None,
        max_docs: int = 8,
    ) -> dict:
        """Answer one coaching turn. Returns {answer, sources, route, context}."""
        route = self.router.route(query, user_role=user_role, user_map=user_map)
        docs = self.retriever.retrieve(
            route["retrieval_queries"],
            collections=route["target_collections"],
            top_k_per_query=3,
            max_docs=max_docs,
        )

        ctx = _format_knowledge(docs)
        header = (
            f"ROLE(사용자 역할군): {role_label(route.get('role'))}\n"
            f"MAP(맵): {route.get('map') or '미지정'}\n"
            f"감지된 영웅: {', '.join(route.get('heroes') or []) or '없음'}\n\n"
        )
        user_content = (
            f"{header}"
            f"KNOWLEDGE(검색된 근거):\n{ctx}\n\n"
            f"질문: {query}\n\n"
            "위 KNOWLEDGE만 근거로, 코치로서 답해줘."
        )

        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        messages.extend(history or [])
        messages.append({"role": "user", "content": user_content})

        try:
            # Low temperature for faithful, grounded answers (less free-styling).
            answer = chat_with_exaone(messages, model=self.model, temperature=0.3)
        except (LLMUnavailable, RuntimeError) as exc:
            answer = f"(모델 호출 실패: {exc})"

        return {
            "answer": answer.strip(),
            "sources": _collect_sources(docs),
            "route": route,
            "context": docs,
        }
