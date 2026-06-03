# PRD: Overwatch Knowledge Chatbot

**문서 버전:** v0.3
**작성일:** 2026-05-31
**프로젝트명:** Overwatch Knowledge Chatbot (오버워치 지식 챗봇)
**대상 시스템:** 오버워치/오버워치 챔피언스 시리즈(OWCS) 도메인에 해박한 한국어 Q&A 챗봇
**핵심 모델:**

- 답변 생성(한국어 LLM): `hf.co/LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-GGUF:Q4_K_M`
- 임베딩: `nomic-embed-text` (대안: `bge-m3`)
- 벡터 DB: ChromaDB (로컬)
- 런타임: Ollama (로컬)

> **작전 변경 기록(v0.2 → v0.3):** 본 프로젝트는 원래 "Vision-to-Caster: 멀티모달 AI 기반 오버워치 e스포츠 해설 생성 시스템"이었다. v0.3에서 **영상/이미지 기반 해설 생성(VLM)** 범위를 제거하고, **텍스트 기반 오버워치 지식 챗봇**으로 방향을 전환한다. 단, 그동안 구축한 **Agentic RAG 지식베이스는 그대로 챗봇의 근거 지식으로 재사용**한다.

---

## 1. 제품 개요

Overwatch Knowledge Chatbot은 오버워치 2와 OWCS(오버워치 챔피언스 시리즈)에 대한 질문을 받아, **로컬 지식베이스(RAG)에 근거해 환각 없이** 한국어로 답하는 대화형 챗봇이다.

영웅·스킬·궁극기·특전(Perks)·맵·팀 조합·중계 용어, 그리고 OWCS 리그 구조·팀·선수 정보를 검색해 답변의 근거로 사용한다. 일반 LLM이 오버워치 세부 사실(영웅 스킬, 특전, 최신 로스터 등)을 자주 지어내는 문제를, **검색 증강 생성(RAG) + 출처 표기 + 근거 없는 단정 금지**로 해결하는 것이 핵심이다.

본 시스템은 **로컬 오프라인 동작**을 목표로 하며, 모든 추론(임베딩·생성)은 로컬 Ollama 런타임에서 수행한다.

---

## 2. 배경 및 문제 정의

오버워치는 영웅 51명, 각 영웅의 다수 스킬·궁극기, 시즌마다 바뀌는 특전(Perks), 30여 개의 맵, 다양한 조합 메타, 그리고 빠르게 변하는 OWCS 리그/로스터를 가진다. 범용 LLM은:

1. 존재하지 않는 영웅·스킬·궁극기를 만들어낸다(환각).
2. 오래된/틀린 OWCS 로스터를 사실처럼 말한다.
3. 시즌마다 바뀌는 특전을 임의로 지어낸다.
4. 한국어 공식 명칭(영웅명/스킬명)을 틀린다.

따라서 본 챗봇은 **검증 가능한 로컬 지식베이스**를 근거로만 답하고, 근거가 없으면 "확인 불가"를 명시한다.

```text
사용자 질문(한국어)
  → 질의 분석 & 컬렉션 라우팅 (Agentic)
  → RAG 검색 (하이브리드 시맨틱+키워드, ChromaDB)
  → 근거 문서 + 출처 수집
  → EXAONE 답변 생성 (근거 밖 사실 금지)
  → 출처/스냅샷 표기와 함께 응답
```

---

## 3. 목표

### 3.1 제품 목표

- 오버워치 영웅/스킬/궁극기/특전/맵/조합/용어 질문에 정확히 답한다.
- OWCS 리그 구조·팀·선수(스냅샷 기준) 질문에 출처와 함께 답한다.
- 답변은 **검색된 근거 범위 안에서만** 생성하고, 근거가 없으면 모른다고 답한다.
- 모든 답변에 사용한 근거의 출처(`source_url`)와 스냅샷/시즌을 제시할 수 있다.
- 멀티턴 대화에서 직전 맥락을 유지한다.
- 전 과정을 로컬에서 실행한다.

### 3.2 연구/검증 목표

- RAG 적용 시 환각률이 RAG 미적용(순수 LLM) 대비 얼마나 감소하는지 평가한다.
- 한국어 도메인 질의에서 하이브리드 검색(시맨틱+키워드)의 정확도를 측정한다.
- "모른다"를 적절히 말하는 비율(거절 정확도)을 평가한다.

---

## 4. 비목표 (Non-goals)

- 이미지/영상 입력 및 멀티모달 해설 생성(이전 VLM 범위 전면 제외).
- 실시간 라이브 중계 텍스트/음성 생성.
- 음성 합성(TTS), 자막/영상 muxing.
- 오버워치 게임 클라이언트/내부 API 연동.
- 실시간 패치·로스터 자동 동기화(지식 갱신은 수동 재인덱싱).
- 상업 배포(EXAONE NC 라이선스 → 비상업 연구/데모로 한정).

---

## 5. 주요 제약 및 전제

### 5.1 런타임/모델

| 컴포넌트 | 런타임 | 모델/라이브러리 | 비고 |
|---|---|---|---|
| 답변 생성 | Ollama | `EXAONE-3.5-7.8B-Instruct-GGUF:Q4_K_M` | 한국어 답변 |
| 임베딩 | Ollama | `nomic-embed-text` (대안 `bge-m3`) | RAG 검색 |
| 벡터 DB | Local Python | ChromaDB | 영속 인덱스 |
| 검색 | Python | 하이브리드(시맨틱+IDF 키워드) | `app/rag/retriever.py` |
| 오케스트레이션 | Python | 챗 루프 + 질의 라우터 | |

> 대안 한국어 모델로 `hf.co/mykor/A.X-4.0-Light-gguf:Q4_K_M`도 로컬에 존재하며, 답변 생성기 교체 후보로 평가 가능하다.

### 5.2 지식 최신성

- 영웅 로스터·OWCS 팀/선수·특전은 시즌마다 변한다. 모든 관련 문서는 `snapshot`(예: `2025_world_finals`, `liquipedia_2026`)과 `source_url`을 가지며, 챗봇은 이를 "스냅샷 기준"으로 답하고 "현재"라고 단정하지 않는다.

### 5.3 라이선스

- EXAONE 3.5는 `EXAONE AI Model License 1.1 - NC`(비상업)로, 본 챗봇은 **비상업 연구/데모**로 한정한다. 상업화 전 별도 검토 필요.
- 지식베이스 출처(Liquipedia, Overwatch Fandom, Namuwiki, Inven 등)의 2차 이용 범위를 공개 배포 시 검토한다.

---

## 6. 사용자 및 사용 시나리오

### 6.1 사용자

| 사용자 | 목적 |
|---|---|
| 오버워치 플레이어 | 영웅 스킬/특전/카운터/맵/조합 학습 |
| e스포츠 팬 | OWCS 리그·팀·선수 정보 확인 |
| 신규 유저 | 용어·역할군·기본 전략 이해 |
| 콘텐츠 제작자/해설 지망생 | 조합·전략·용어 빠른 참조 |

### 6.2 대표 질문(의도) 예시

- "겐지 궁극기 이름이 뭐야?" → hero_abilities
- "키리코 특전 알려줘" → hero_perks
- "다이브 조합이 뭐고 어떻게 상대해?" → team_comps
- "OWCS 2025 월드 파이널 우승팀은?" → league_info / teams
- "크레이지 라쿤 로스터 알려줘" → players / teams
- "왕의 길은 어떤 맵이야?" → map_strategy
- "C9이 무슨 뜻이야?" → league_terms
- "오버워치 2에 영웅 몇 명이야?" → hero_abilities(개요)

---

## 7. 기능 요구사항

### FR-1. 질의 입력

CLI 또는 대화 인터페이스로 한국어 질문을 입력받는다.
- **Acceptance:** 빈 입력·과도하게 긴 입력을 안전하게 처리한다.

### FR-2. Agentic 질의 분석 및 컬렉션 라우팅

질의를 분석해 어떤 지식 컬렉션을 검색할지 결정하고 검색 질의를 생성한다.
- 컬렉션: `hero_abilities`, `hero_perks`, `map_strategy`, `team_comps`, `league_info`, `teams`, `players`, `league_terms`, `tactical_patterns`, `caster_style_ko`.
- **Acceptance:** "특전" 질문은 `hero_perks`, 선수 질문은 `players`/`teams`로 라우팅된다. (`app/vlm/scene_planner.py`의 라우팅 로직을 챗봇 질의 라우터로 재사용/일반화)

### FR-3. RAG 검색

라우팅된 컬렉션에서 하이브리드(시맨틱+IDF 키워드) 검색을 수행한다.
- **Acceptance:** segment/질의당 최대 N개 문서 반환, 약한 결과 시 self-query 재검색(`AgenticRetriever.retrieve`). 임베딩/DB 부재 시 키워드 폴백.

### FR-4. 근거 기반 답변 생성

EXAONE이 **검색된 근거 문서만** 사용해 한국어로 답한다.
- **Acceptance:** 근거에 없는 영웅/스킬/특전/선수/팀/수치를 만들지 않는다. 한국어가 자연스럽다.

### FR-5. 출처 표기

답변에 사용한 근거의 `source_url`과 `snapshot`/`season`을 제시한다.
- **Acceptance:** 로스터·특전 답변에는 스냅샷/출처가 함께 표시된다.

### FR-6. 환각 방지 / 거절

검색 결과가 없거나 신뢰도가 낮으면 단정하지 않고 "확인되는 정보가 없다"고 답한다.
- **Acceptance:** KB에 없는 가상의 영웅/사실을 물으면 지어내지 않고 모른다고 답한다.

### FR-7. 멀티턴 대화 맥락

직전 대화 맥락(대명사, 후속 질문)을 유지한다.
- **Acceptance:** "그럼 그 영웅 카운터는?" 같은 후속 질문이 직전 주제를 이어받는다.

### FR-8. 지식 갱신

KB(JSONL) 추가/수정 후 재인덱싱으로 지식을 갱신한다.
- **Acceptance:** `python -m app.rag.build_index --reset`로 인덱스가 갱신되고 챗봇이 새 지식을 사용한다.

---

## 8. 지식베이스 범위 (이미 구축됨)

`app/rag/kb/*.jsonl` + `app/rag/knowledge_base.jsonl`, 총 약 286개 문서. 모든 문서는 `metadata.collection`과 출처(`source`/`source_url`)를 가진다.

| 컬렉션 | 문서 수(약) | 내용 | 출처 |
|---|---:|---|---|
| `hero_abilities` | 53 | 영웅 51명 역할·세부역할·궁극기·스킬 + 로스터/특전 개요 | 일반 지식 + 신규 영웅 Liquipedia |
| `hero_perks` | 51 | 영웅별 특전(소소한 2 + 강력한 2), 시즌 변동 | Liquipedia per-hero |
| `map_strategy` | 38 | 6개 모드 + 31개 맵(경쟁전 29 + Clash 2) | 일반 지식 + Liquipedia |
| `team_comps` | 8 | 조합 아키타입(다이브/브롤/포크/벙커 등) 전략·상성 | 일반 지식(Namuwiki 조합 참조) |
| `league_terms` | 39 | 한국 중계 용어 | Namuwiki 용어 / Inven |
| `league_info` | 7 | OWCS 구조·지역·스테이지·이벤트 | Liquipedia/공식 |
| `teams` | 12 | OWCS 2025 월드 파이널 팀 | Liquipedia |
| `players` | 70 | OWCS 2025 월드 파이널 선수 | Liquipedia |
| `tactical_patterns`, `caster_style_ko` | 8 | 전술/한국어 표현 | 일반 지식 |

**확장 예정(태스크):** 영웅별 상성(상성)/궁합 → `matchups` 컬렉션(출처: Namuwiki 영웅 페이지 #s-10, 접근 차단 해소 후 수집).

---

## 9. 아키텍처 요약 (재사용 자산)

```text
app/rag/
├─ kb/                  # 지식베이스 JSONL (영웅/특전/맵/조합/용어/리그/팀/선수)
├─ kb_loader.py         # 통합 로드/중복 제거
├─ embeddings.py        # Ollama 임베딩(nomic-embed-text), NaN 방어
├─ build_index.py       # ChromaDB 영속 인덱스 빌드
├─ retriever.py         # AgenticRetriever: 하이브리드 검색 + 컬렉션 라우팅 + self-query
├─ roster.py            # 이름 검증/오독 교정(챗봇에서 입력 영웅/선수명 정규화에 활용)
└─ context_builder.py   # (해설용) → 챗 답변 컨텍스트 조립기로 일반화 예정

신규(챗봇):
├─ chat/query_router.py # 질의 → 컬렉션 라우팅 + 검색 질의 (scene_planner 일반화)
├─ chat/answer.py       # 근거 컨텍스트 + EXAONE 답변 생성
└─ chat/cli.py          # 대화 루프(멀티턴)
```

기존 `app/vlm/`, `app/video/`, `app/timeline/`, `app/generation/`의 해설/영상 전용 모듈은 챗봇 범위에서 제외(보관 또는 정리 대상).

---

## 10. 비기능 요구사항

- **성능:** 단일 질문 응답 30초 이내(로컬 Q4_K_M 기준) 목표.
- **안정성:** LLM 호출 실패/JSON 파싱 실패 시 안전한 폴백 응답.
- **재현성:** 사용 모델·임베딩·인덱스 스냅샷·프롬프트 버전 기록.
- **보안/프라이버시:** 외부 API 호출 없이 로컬 추론(지식 수집 단계 제외).
- **관측성:** 질의·라우팅된 컬렉션·검색 문서·출처·지연을 로깅.

---

## 11. 평가 지표

| 지표 | 목표 |
|---|---:|
| 도메인 정답 정확도(샘플 Q&A) | ≥ 0.85 |
| 환각률(근거 밖 사실 단정) | ≤ 5% |
| 출처 표기 정확도 | ≥ 0.9 |
| "모름" 적절성(KB 밖 질문 거절) | ≥ 0.9 |
| 한국어 자연스러움(휴먼) | ≥ 4.0 / 5 |
| 검색 top-1 적중률(라우팅된 컬렉션 내) | ≥ 0.8 |

---

## 12. 단계별 계획

### Phase 0. 모델/런타임 검증
- EXAONE 한국어 답변 smoke test, 임베딩 모델 동작 확인, ChromaDB 인덱스 로드 확인.

### Phase 1. RAG 백엔드 (구축 완료)
- KB 286문서, 하이브리드 retriever, ChromaDB 인덱스 — 이미 동작.

### Phase 2. 챗 루프 + 답변 생성
- 질의 라우터(scene_planner 일반화), 근거 컨텍스트 조립, EXAONE 답변 프롬프트, 출처 표기.

### Phase 3. 멀티턴 + 환각 방지
- 대화 맥락 유지, 근거 없음 시 거절, self-query 재검색 연결.

### Phase 4. 평가/개선
- 샘플 Q&A 세트로 정확도·환각률·거절 정확도 측정, 프롬프트/라우팅 개선.

---

## 13. 최종 Acceptance Checklist

- [ ] EXAONE 한국어 답변 smoke test 통과
- [ ] ChromaDB 인덱스 로드 및 하이브리드 검색 동작
- [ ] 질의 라우팅: 특전/선수/맵/조합/용어 질문이 올바른 컬렉션으로 검색됨
- [ ] 근거 기반 답변 생성(근거 밖 사실 생성 금지)
- [ ] 답변에 출처/스냅샷 표기
- [ ] KB 밖 질문에 "확인 불가"로 응답(환각 방지)
- [ ] 멀티턴 후속 질문 맥락 유지
- [ ] EXAONE 비상업 라이선스 제한이 README와 응답 정책에 명시됨

---

## 14. 참고 근거

- EXAONE 3.5 7.8B Instruct GGUF: https://huggingface.co/LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-GGUF
- Hugging Face Hub Ollama GGUF 문서: https://huggingface.co/docs/hub/ollama
- 지식베이스 출처: Liquipedia Overwatch(영웅/특전/맵/리그/팀/선수), Overwatch Fandom, Namuwiki(용어/조합/영웅), Inven(용어)
- RAG 서브시스템 상세: `docs/RAG.md`
