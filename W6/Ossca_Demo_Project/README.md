# computer_advisor_rag

컴퓨터 구매를 고민하는 사용자에게 목적, 예산, 이동성, 사용 환경에 맞는 컴퓨터 형태와 사양을 추천하는 Agentic RAG 데모 프로젝트입니다.

## 목표

- 사용자의 구매 목적과 환경을 파악한다.
- 정보가 부족하면 추가 질문을 한다.
- 컴퓨터 구매 가이드 문서를 검색 도구로 조회한다.
- 일반 데스크탑, 소형 데스크탑, 노트북 중 적합한 형태를 추천한다.
- CPU, GPU, RAM, 저장장치 실 제품 후보와 예산대를 함께 설명한다.

## 문서 구성

- `docs/buying_guide.md`: 사용 목적별 컴퓨터 추천 기준
- `docs/form_factor_guide.md`: 일반 데스크탑, 소형 데스크탑, 노트북 비교
- `docs/spec_guide.md`: CPU, GPU, RAM, 저장장치 선택 기준
- `docs/budget_guide.md`: 예산대별 현실적인 구성 기준
- `docs/os_ecosystem_guide.md`: Windows, macOS, Linux 선택 기준
- `docs/recommendation_policy.md`: Agent가 상담할 때 따를 추천 정책

## 제품 카탈로그

- `data/cpus.json`: 데스크탑 CPU 후보
- `data/gpus.json`: 데스크탑 GPU 후보
- `data/memory.json`: RAM 구성 후보
- `data/storage.json`: SSD 후보

제품 카탈로그는 실시간 가격표가 아니라 추천 후보 데이터입니다.
가격과 재고는 변동될 수 있으므로, Agent는 구체적인 최저가 대신 예산 등급과 용도 적합성을 기준으로 추천합니다.

## 예정 구현 단계

1. 문서 기반 Chroma Vector DB 생성
2. 구매 가이드 검색 Tool 정의
3. 제품 카탈로그 추천 Tool 정의
4. CLI 기반 Agentic RAG 챗봇 구현
5. 테스트 질문으로 Tool 호출 여부와 추천 품질 확인

## 실행 방법

Ollama가 설치되어 있고 `llama3.1` 모델을 사용할 수 있어야 합니다.

```bash
pip install -r requirements.txt
python step1_build_vectordb.py
python step2_define_tools.py
python step3_agent_chat.py
```

`step1_build_vectordb.py`는 `docs/` 문서를 임베딩해서 `chroma_db/`에 저장합니다.
`step3_agent_chat.py`는 대화형 CLI 챗봇을 실행합니다.

## API 실행

프론트엔드는 FastAPI 서버의 `/chat` 엔드포인트를 호출하면 됩니다.

```bash
uvicorn api.main:app --reload --port 8000
```

요청 예시:

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"150만원 개발 게임용 데스크탑 추천해줘"}'
```

`session_id`를 생략하거나 빈 문자열로 보내면 백엔드가 새 세션 ID를 생성해 응답에 돌려줍니다.
같은 대화를 이어가려면 응답으로 받은 `session_id`를 다음 요청부터 계속 보내면 됩니다.
응답에는 세션 ID, 최종 답변, Tool 호출 내역, 참조 문서/데이터, 히스토리 크기, 사용 모델 정보가 포함됩니다.

후속 요청 예시:

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"session_id":"응답으로_받은_session_id","message":"우분투 쓰고 도커를 많이 돌릴거야"}'
```

세션 초기화:

```bash
curl -X DELETE http://localhost:8000/sessions/demo-user
```

## Ollama 모델 운용

모델 설정은 `config.py`에서 관리합니다.

- 채팅 모델: `llama3.1`
- 임베딩 모델: `llama3.1`
- keep alive: 180초

API 서버 시작 시 현재 Ollama에 로드된 모델 중 프로젝트에서 쓰지 않는 모델은 정리합니다.
`/health` 응답에서 현재 설정된 모델과 실행 중인 Ollama 모델 목록을 확인할 수 있습니다.
