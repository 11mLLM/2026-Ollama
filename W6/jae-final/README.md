# Codex 보고서 요약 & RAG CLI

로컬 [Ollama](https://ollama.com/)와 [ChromaDB](https://www.trychroma.com/)만으로 동작하는,
Codex 작업 보고서 요약 / 검색(RAG) 커맨드라인 도구입니다.

대상 프로젝트의 `.codex/reports`(또는 `.codex/harness/reports`)에 쌓인 작업 보고서를 자동으로
찾아 그룹으로 묶고, Ollama LLM으로 한국어 Markdown 요약을 만들거나, Chroma 벡터 DB에 색인해
의미 기반 검색·질의응답을 합니다. 외부 API 키 없이 전부 로컬에서 돌아갑니다.

## 주요 기능

- **보고서 자동 탐색**: 같은 작업의 `main` / `payload-json` / `backend-gate` / `pr-body` 등 관련
  파일을 하나의 그룹(slug)으로 묶고, 수정 시각 기준으로 최신순 정렬합니다.
- **요약 (`summarize`)**: 최근 N개 또는 특정 날짜의 보고서를 정해진 heading 순서의 Markdown으로 요약.
  입력이 길면 그룹별 중간 요약 후 최종 요약하는 map-reduce 방식으로 처리합니다.
- **RAG 색인/검색 (`index` / `reindex` / `search` / `ask`)**: 보고서를 청크로 나눠 임베딩 후
  Chroma에 저장하고, 질문과 가장 가까운 근거를 찾아 답변을 생성합니다.
- **상태 확인 (`stats`)**: 색인된 청크 수와 보고서 파일 수를 출력합니다.
- **셀프 테스트 (`--self-test`)**: 임시 디렉터리로 탐색·청킹 로직을 검증합니다 (Ollama 불필요).

## 사전 준비

1. **Python 3.10+** (`str | None` 등 최신 타입 문법 사용)
2. **Ollama** 설치 및 실행 (`http://localhost:11434`)
3. 사용할 모델 받기:
   ```bash
   ollama pull llama3.1        # 요약/답변 생성용
   ollama pull embeddinggemma  # RAG 임베딩용
   ```

## 설치

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt   # chromadb (RAG 기능에만 필요)
```

> `summarize`만 쓴다면 `chromadb` 없이도 동작합니다. RAG 명령(`index`/`search`/`ask`/`stats`)에서만 필요합니다.

## 사용법

진입점은 `scripts/reports.py`이며, `.venv`가 있으면 자동으로 그 인터프리터로 재실행합니다.

```bash
# 전체 셀프 테스트
python3 scripts/reports.py --self-test

# 최근 5개 보고서 그룹 요약
python3 scripts/reports.py summarize --recent 5

# 특정 날짜(수정시각 기준) 보고서 요약 → 파일로 저장
python3 scripts/reports.py summarize --date 2026-05-31 --output summary.md

# Ollama 호출 없이 어떤 보고서가 선택되는지만 확인
python3 scripts/reports.py summarize --recent 3 --dry-run

# RAG 색인 (전체 재색인은 reindex)
python3 scripts/reports.py index
python3 scripts/reports.py reindex

# 검색 결과만 출력 / 근거 기반 질의응답
python3 scripts/reports.py search "백엔드 게이트 실패 원인" --top-k 6
python3 scripts/reports.py ask "최근 PR에서 남은 위험은?"

# 색인 상태 확인
python3 scripts/reports.py stats
```

`scripts/summarize_reports.py`는 `summarize`만 바로 호출하는 하위호환 래퍼입니다.

### 자주 쓰는 옵션

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `--project-dir` | `~/home-search` (없으면 도구 루트) | 보고서를 찾을 대상 프로젝트 루트 |
| `--reports-dir` | 자동 탐색 | 보고서 디렉터리 직접 지정 |
| `--model` | `llama3.1` | 요약/답변 생성 모델 |
| `--embed-model` | `embeddinggemma` | RAG 임베딩 모델 |
| `--ollama-url` | `http://localhost:11434` | Ollama 서버 주소 |
| `--db-dir` | `<루트>/.data/chroma` | Chroma 영구 저장 경로 |
| `--collection` | `reports_v1` | Chroma collection 이름 |
| `--timeout` | `120` | Ollama 요청 타임아웃(초) |
| `--top-k` | `6` | (search/ask) 가져올 근거 청크 수 |

환경변수 `HOME_SEARCH_PROJECT_DIR`로 기본 대상 프로젝트 경로를 바꿀 수 있습니다.

## 프로젝트 구조

```
jae-final/
├── README.md
├── requirements.txt          # chromadb (RAG 의존성)
├── reports/                  # 핵심 패키지
│   ├── cli.py                # 서브커맨드 라우팅
│   ├── discovery.py          # 보고서 탐색·그룹핑·문서 로딩
│   ├── summarize.py          # 비-RAG 요약 로직 (map-reduce)
│   ├── rag.py                # Chroma 색인/검색/질의응답
│   ├── ollama_client.py      # stdlib 기반 Ollama HTTP 클라이언트
│   └── prompts.py            # 요약/답변 프롬프트 템플릿
└── scripts/
    ├── reports.py            # 메인 진입점 (.venv 자동 재실행)
    └── summarize_reports.py  # summarize 하위호환 래퍼
```

## 종료 코드

| 코드 | 의미 |
| --- | --- |
| `0` | 성공 |
| `2` | Ollama 또는 모델 사용 불가(preflight 실패) |
| `3` | 선택된 보고서 없음 |
| `4` | Ollama 생성/임베딩 실패 |
| `5` | 보고서 읽기/파싱 실패 |
| `6` | RAG 의존성(`chromadb`) 미설치 |
| `7` | Chroma 조회 실패 |