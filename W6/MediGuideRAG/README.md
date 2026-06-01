# 🩺 어디 아파요? — Agentic RAG 진료과 안내 도우미

증상을 입력하면 **가능한 원인 + 권장 진료과 + 응급 신호**를 안내하는 Ollama 기반 챗봇.
LLM이 "이 질문에 자료 검색이 필요한가?"를 스스로 판단하는 **Agentic RAG** 구조다.

> ⚠️ 진단 도구가 아니라 참고용. 정확한 진단·치료는 의료진에게.

## 동작 방식
- 인사·잡담 → 도구 없이 바로 답변 (`🧠 일반 안내`)
- 증상 질문 → 벡터DB에서 자료 검색 후 답변 (`📋 증상 가이드 검색`)

```
사용자 입력 → LLM 판단 →┬─ 검색 필요? → lookup_symptom_guide(ChromaDB) → 최종 답변
                        └─ 불필요 → 바로 답변
```

## 설치 & 실행
```bash
# 0) Ollama 모델 준비 (최초 1회)
ollama pull llama3.1

# 1) 패키지 설치
pip install -r requirements.txt

# 2) 벡터DB 생성 (knowledge/symptoms.md → chroma_db)
python ingest.py

# 3) 앱 실행
streamlit run app.py
```

## 파일 구성
- `knowledge/symptoms.md` — 지식베이스(증상별 안내). **이 파일만 바꾸면 주제가 바뀜**
- `ingest.py` — 문서 분할 + 임베딩 + 벡터DB 저장
- `app.py` — Streamlit 챗봇 + Agentic RAG 로직

## 바꿔보기
- **모델 교체**: `app.py`/`ingest.py` 상단 `CHAT_MODEL`, `EMBED_MODEL` 수정
  (툴 호출은 `llama3.1`, `qwen2.5` 등 지원 모델 필요. gemma 일부 버전은 툴 미지원)
- **주제 교체**: `knowledge/` 문서 교체 → 툴 설명(docstring)과 시스템 프롬프트 수정 → `python ingest.py` 재실행
- **임베딩 속도 개선**: `ollama pull nomic-embed-text` 후 `EMBED_MODEL="nomic-embed-text"`로 변경, ingest 재실행
