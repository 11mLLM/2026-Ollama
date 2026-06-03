"""Agentic retriever for the Vision-to-Caster RAG.

Retrieval has three layers:

1. **Semantic** — ChromaDB + Ollama ``nomic-embed-text`` (preferred). Supports
   per-collection routing via metadata filters.
2. **Keyword fallback** — token-overlap scoring over in-memory documents when
   the embedding backend or ChromaDB index is unavailable. The pipeline never
   crashes just because the vector store is missing.
3. **Agentic self-query** — when a query returns weak/empty results the
   retriever rewrites it (collection-aware expansion) and retries once, so the
   Qwen planner's queries do not have to be perfectly phrased.

The legacy ``load_knowledge_base`` / ``retrieve_context`` functions are kept so
existing call sites keep working.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

from app.rag.build_index import CHROMA_COLLECTION, CHROMA_DIR
from app.rag.embeddings import EMBED_MODEL, EmbeddingUnavailable, embed_query
from app.rag.kb_loader import LEGACY_KB, load_documents

# Tokens that add no retrieval signal in mixed KO/EN esports queries.
_STOPWORDS = {"의", "은", "는", "이", "가", "을", "를", "에", "and", "the", "of", "for", "a"}

# Collection-aware query expansion terms for the self-query retry step.
_EXPANSION_TERMS = {
    "hero_abilities": "영웅 궁극기 스킬 역할",
    "hero_perks": "특전 퍼크 소소한 강력한",
    "map_strategy": "맵 모드 거점 화물 지형",
    "league_info": "OWCS 오버워치 챔피언스 시리즈 리그 지역 스테이지",
    "teams": "OWCS 팀 소속 지역",
    "players": "OWCS 선수 소속 팀 포지션",
    "league_terms": "오버워치 중계 용어 전술",
    "tactical_patterns": "전술 한타 궁극기 운영",
    "team_comps": "조합 다이브 브롤 포크 벙커 전략 상성",
    "matchups": "상성 카운터 약점 강점 궁합 대응",
    "caster_style_ko": "한국어 중계 문체 표현",
}


# --------------------------------------------------------------------------- #
# Legacy API (kept for backward compatibility)
# --------------------------------------------------------------------------- #
def load_knowledge_base(path: Path = LEGACY_KB) -> list[dict]:
    """Load the unified KB. ``path`` is accepted for backward compatibility but
    the full ``kb/`` directory is always merged in."""
    return load_documents()


def _tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[A-Za-z0-9]+|[가-힣]+", text.lower())
    return [t for t in tokens if t and t not in _STOPWORDS]


def _keyword_score(query_tokens: list[str], doc_text: str) -> float:
    if not query_tokens:
        return 0.0
    doc_lower = doc_text.lower()
    hits = 0
    for token in query_tokens:
        if token in doc_lower:
            hits += 1
    return hits / len(query_tokens)


def retrieve_context(queries: list[str], documents: list[dict], top_k: int = 5) -> list[dict]:
    """Keyword-style retrieval over in-memory documents (legacy signature)."""
    if not documents:
        return []
    if not queries:
        return documents[:top_k]
    query_tokens = _tokenize(" ".join(queries))
    scored = [
        (_keyword_score(query_tokens, doc.get("text", "")), doc) for doc in documents
    ]
    scored.sort(key=lambda item: item[0], reverse=True)
    return [doc for score, doc in scored[:top_k] if score > 0] or documents[:top_k]


# --------------------------------------------------------------------------- #
# Agentic retriever
# --------------------------------------------------------------------------- #
class AgenticRetriever:
    """Routes queries to collections, prefers semantic search, falls back to
    keyword, and self-queries once when results are weak."""

    def __init__(
        self,
        embed_model: str = EMBED_MODEL,
        min_semantic_score: float = 0.25,
        semantic_weight: float = 0.15,
    ) -> None:
        self.embed_model = embed_model
        self.min_semantic_score = min_semantic_score
        # Hybrid blend weight: nomic-embed-text is noisy on Korean, so literal
        # keyword overlap (hero/player/term names) dominates and semantic acts as
        # a light paraphrase signal. Raise this if you switch to a strong
        # multilingual embedder (e.g. bge-m3) whose Korean vectors are reliable.
        self.semantic_weight = semantic_weight
        self.documents = load_documents()
        self._by_id = {doc["id"]: doc for doc in self.documents}
        self._doc_tokens, self._idf = self._build_token_stats()
        self._chroma = self._connect_chroma()
        self.backend = "semantic" if self._chroma is not None else "keyword"

    def _build_token_stats(self) -> tuple[dict[str, set[str]], dict[str, float]]:
        """Tokenize every doc and compute IDF so distinctive tokens (a player's
        name) outweigh boilerplate shared across docs (선수/소속/팀/OWCS)."""
        doc_tokens: dict[str, set[str]] = {}
        df: dict[str, int] = {}
        for doc in self.documents:
            tokens = set(_tokenize(doc.get("text", "")))
            doc_tokens[doc["id"]] = tokens
            for token in tokens:
                df[token] = df.get(token, 0) + 1
        total = max(1, len(self.documents))
        idf = {tok: math.log((total + 1) / (count + 1)) + 1.0 for tok, count in df.items()}
        return doc_tokens, idf

    # -- backend wiring ----------------------------------------------------- #
    def _connect_chroma(self):
        try:
            import chromadb
        except ImportError:
            return None
        if not CHROMA_DIR.exists():
            return None
        try:
            client = chromadb.PersistentClient(path=str(CHROMA_DIR))
            collection = client.get_collection(CHROMA_COLLECTION)
            # Probe embeddings once; if the model is gone, drop to keyword mode.
            embed_query("probe", model=self.embed_model)
            return collection
        except (EmbeddingUnavailable, Exception):  # noqa: BLE001
            return None

    @staticmethod
    def _where_for_collections(collections: list[str] | None):
        if not collections:
            return None
        if len(collections) == 1:
            return {"collection": collections[0]}
        return {"collection": {"$in": collections}}

    # -- single-query retrieval --------------------------------------------- #
    def _pool(self, collections: list[str] | None) -> list[dict]:
        if not collections:
            return self.documents
        allowed = set(collections)
        return [d for d in self.documents if (d.get("metadata") or {}).get("collection") in allowed]

    def _semantic_scores(
        self, query: str, collections: list[str] | None, top_k: int
    ) -> dict[str, float]:
        vector = embed_query(query, model=self.embed_model)
        result = self._chroma.query(
            query_embeddings=[vector],
            n_results=top_k,
            where=self._where_for_collections(collections),
        )
        ids = (result.get("ids") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]
        scores: dict[str, float] = {}
        for idx, doc_id in enumerate(ids):
            distance = distances[idx] if idx < len(distances) else 1.0
            scores[doc_id] = max(0.0, 1.0 - float(distance))  # cosine -> similarity
        return scores

    def _keyword_scores(self, query: str, collections: list[str] | None) -> dict[str, float]:
        """IDF-weighted overlap, normalized to [0, 1] per query.

        Matching is substring-based against the document text rather than exact
        token-set membership: Korean is agglutinative, so a query token like
        "진영" must still match the joined form "진영붕괴", and "각개격파" must
        match "각개격파로". IDF weights (from token frequencies) still suppress
        boilerplate tokens shared across many docs."""
        query_tokens = list(dict.fromkeys(_tokenize(query)))
        if not query_tokens:
            return {}
        weights = {t: self._idf.get(t, 1.0) for t in query_tokens}
        total_weight = sum(weights.values()) or 1.0
        scores: dict[str, float] = {}
        for doc in self._pool(collections):
            text = (doc.get("text") or "").lower()
            matched = sum(weights[t] for t in query_tokens if t in text)
            scores[doc["id"]] = matched / total_weight
        return scores

    def _search_once(
        self, query: str, collections: list[str] | None, top_k: int
    ) -> list[dict]:
        """Hybrid search: blend semantic similarity with literal keyword overlap.

        Falls back to pure keyword scoring if the vector backend errors out.
        """
        keyword = self._keyword_scores(query, collections)
        semantic: dict[str, float] = {}
        retrieval = "keyword"
        if self._chroma is not None:
            try:
                semantic = self._semantic_scores(query, collections, top_k * 3)
                retrieval = "hybrid"
            except (EmbeddingUnavailable, Exception):  # noqa: BLE001
                self._chroma = None
                self.backend = "keyword"

        w = self.semantic_weight
        ids = set(keyword) | set(semantic)
        hits: list[dict] = []
        for doc_id in ids:
            sem = semantic.get(doc_id, 0.0)
            kw = keyword.get(doc_id, 0.0)
            blended = (w * sem + (1 - w) * kw) if semantic else kw
            if blended <= 0:
                continue
            base = self._by_id.get(doc_id, {"id": doc_id})
            hits.append({**base, "_score": blended, "_retrieval": retrieval})
        hits.sort(key=lambda d: d["_score"], reverse=True)
        return hits[:top_k]

    def _expand_query(self, query: str, collections: list[str] | None) -> str:
        extra = " ".join(
            _EXPANSION_TERMS.get(c, "") for c in (collections or [])
        ).strip()
        return f"{query} {extra}".strip() if extra else query

    # -- public API --------------------------------------------------------- #
    def retrieve(
        self,
        queries: list[str],
        collections: list[str] | None = None,
        top_k_per_query: int = 3,
        max_docs: int = 8,
        min_score: float = 0.2,
    ) -> list[dict]:
        """Run all queries, dedup by id (keeping best score), and return the
        strongest ``max_docs`` whose score clears ``min_score``. Performs one
        agentic self-query retry when a query's best result is weak.

        ``min_score`` trims near-miss noise (e.g. a map doc that only shared the
        token "맵"); at least the single best doc is always returned so a segment
        is never left with empty context."""
        best: dict[str, dict] = {}

        def _ingest(hits: list[dict]) -> float:
            top = 0.0
            for hit in hits:
                doc_id = hit.get("id")
                if not doc_id:
                    continue
                top = max(top, hit.get("_score", 0.0))
                prior = best.get(doc_id)
                if prior is None or hit.get("_score", 0.0) > prior.get("_score", 0.0):
                    best[doc_id] = hit
            return top

        for query in queries or [""]:
            hits = self._search_once(query, collections, top_k_per_query)
            top_score = _ingest(hits)
            # Agentic self-query: weak/empty result -> rewrite and retry once.
            weak = (not hits) or (self.backend == "semantic" and top_score < self.min_semantic_score)
            if weak:
                rewritten = self._expand_query(query, collections)
                if rewritten != query:
                    _ingest(self._search_once(rewritten, collections, top_k_per_query))

        ranked = sorted(best.values(), key=lambda d: d.get("_score", 0.0), reverse=True)
        filtered = [d for d in ranked if d.get("_score", 0.0) >= min_score]
        if not filtered and ranked:
            filtered = ranked[:1]  # never leave a segment with empty context
        return filtered[:max_docs]


def get_retriever() -> AgenticRetriever:
    """Convenience factory."""
    return AgenticRetriever()


if __name__ == "__main__":
    retriever = get_retriever()
    print(f"backend: {retriever.backend}, docs: {len(retriever.documents)}")
    demo = retriever.retrieve(
        ["크레이지 라쿤 선수", "아나 궁극기"],
        collections=["players", "hero_abilities"],
    )
    print(json.dumps(demo, ensure_ascii=False, indent=2))
