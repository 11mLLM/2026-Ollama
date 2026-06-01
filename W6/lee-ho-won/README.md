# Local Life Assistant

Ollama 기반 로컬 개인 생활 어시스턴트입니다.
개인 생활 문서를 기반으로 질문에 답변하고, 메일/메시지 초안을 자연스럽게 다듬으며, 상황별 체크리스트를 생성할 수 있습니다.

## 프로젝트 개요

일상에서 자주 확인해야 하는 규칙, 메일 작성 방식, 발표/논문 준비 체크리스트 등이 여러 곳에 흩어져 있으면 매번 다시 찾고 정리해야 하는 불편함이 있습니다.

이 프로젝트는 이러한 불편함을 줄이기 위해 만든 간단한 로컬 AI 어시스턴트입니다.
Ollama를 활용해 외부 API 없이 로컬 환경에서 LLM을 실행하며, 개인 문서 기반 질의응답에는 RAG 구조를 사용합니다.

## 주요 기능

### 1. 개인 문서 기반 Q&A

`data/personal_policy.md`에 저장된 개인 생활 문서를 ChromaDB 벡터DB로 변환한 뒤, 사용자의 질문과 관련된 내용을 검색하여 답변합니다.

예시 질문:

```text
교수님께 메일 보낼 때 어떻게 써야 해?
논문 제출 전에 확인할 것은 뭐야?
발표 준비할 때 체크해야 할 것은?
```

### 2. 메일/메시지 다듬기

사용자가 입력한 메일 또는 메시지 초안을 한국어 문맥에 맞게 정중하고 자연스럽게 다듬어줍니다.

예시 입력:

```text
교수님 안녕하세요 이호원입니다
보강 강의 링크 보내드립니다
확인 부탁드립니다
```

### 3. 체크리스트 생성

사용자가 입력한 상황을 바탕으로 바로 실행할 수 있는 체크리스트를 생성합니다.

예시 입력:

```text
논문 제출 전에 확인해야 할 것
석사 디펜스 발표 준비
수업 보강 메일 보내기 전 확인할 것
```

## 기술 스택

* Python
* Ollama
* EXAONE 3.5 7.8B
* LangChain
* ChromaDB
* RAG

## 프로젝트 구조

```text
local-life-assistant/
├─ app.py
├─ build_vector_db.py
├─ rag.py
├─ prompts.py
├─ requirements.txt
├─ README.md
├─ .gitignore
├─ data/
│  ├─ personal_policy.md
│  ├─ email_style.md
│  └─ checklist_rules.md
└─ chroma_db/
```

## 파일 설명

| 파일                        | 설명                               |
| ------------------------- | -------------------------------- |
| `app.py`                  | CLI 메뉴 기반 메인 실행 파일               |
| `build_vector_db.py`      | 개인 문서를 ChromaDB 벡터DB로 변환         |
| `rag.py`                  | 벡터DB에서 관련 문서를 검색                 |
| `prompts.py`              | 메일 다듬기, 체크리스트 생성, RAG 답변 프롬프트 관리 |
| `data/personal_policy.md` | 개인 생활 규칙 및 참고 문서                 |
| `requirements.txt`        | 프로젝트 실행에 필요한 Python 패키지 목록       |

## 실행 방법

### 1. Ollama 설치

Ollama를 설치한 뒤, 사용할 모델을 다운로드합니다.

```bash
ollama pull exaone3.5:7.8b
```

모델이 정상적으로 설치되었는지 확인합니다.

```bash
ollama ls
```

### 2. 가상환경 생성 및 활성화

```bash
python -m venv .venv
```

Windows PowerShell 기준:

```bash
.\.venv\Scripts\Activate.ps1
```

Git Bash 기준:

```bash
source .venv/Scripts/activate
```

### 3. 패키지 설치

```bash
python -m pip install -r requirements.txt
```

### 4. 벡터DB 생성

개인 문서를 기반으로 ChromaDB 벡터DB를 생성합니다.

```bash
python build_vector_db.py
```

### 5. 앱 실행

```bash
python app.py
```

실행 후 메뉴에서 원하는 기능을 선택합니다.

```text
1. 개인 문서에 질문하기
2. 메일/메시지 다듬기
3. 체크리스트 만들기
4. 종료
```

## 사용 예시

### 개인 문서 Q&A

```text
질문: 교수님께 메일 보낼 때 어떻게 써야 해?
```

답변 예시:

```text
교수님께 메일을 보낼 때는 정중한 인사로 시작하고, 본인 소속과 이름, 요청 사항을 명확히 작성하는 것이 좋습니다. 마지막에는 감사 인사를 포함하는 것이 좋습니다.
```

### 메일 다듬기

입력:

```text
교수님 안녕하세요 이호원입니다
보강 강의 링크 보내드립니다
확인 부탁드립니다
```

출력 예시:

```text
제목: 보강 강의 링크 전달드립니다

교수님, 안녕하세요.
이호원입니다.

보강 강의 링크를 전달드립니다.
확인 부탁드립니다.

감사합니다.
이호원 드림
```

## RAG 구조

이 프로젝트의 개인 문서 Q&A 기능은 RAG 구조를 사용합니다.

```text
개인 문서
→ 문서 분할
→ 임베딩 생성
→ ChromaDB 저장
→ 사용자 질문 입력
→ 관련 문서 검색
→ LLM 답변 생성
```

이를 통해 LLM이 일반적인 지식만으로 답변하는 것이 아니라, 사용자가 작성한 개인 문서를 참고하여 답변할 수 있습니다.

## 특징

* 외부 LLM API 없이 로컬에서 실행
* 개인 문서를 기반으로 한 RAG 질의응답
* 한국어 메일/메시지 다듬기 지원
* 간단한 CLI 기반 구조
* GitHub에 올리기 쉬운 작은 규모의 프로젝트

## 주의사항

* `chroma_db/`는 `build_vector_db.py`를 통해 다시 생성할 수 있으므로 GitHub에는 포함하지 않습니다.
* `.venv/`는 로컬 가상환경 폴더이므로 GitHub에는 포함하지 않습니다.
* Ollama가 실행 중이어야 앱이 정상적으로 동작합니다.

## 향후 개선 방향

* Streamlit 기반 웹 UI 추가
* 여러 문서 파일을 동시에 RAG에 반영
* 메일 유형 선택 기능 추가
* 체크리스트 결과를 Markdown 파일로 저장
* 임베딩 전용 모델 적용을 통한 검색 품질 개선
