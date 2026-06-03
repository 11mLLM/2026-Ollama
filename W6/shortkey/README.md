# ⌨️ 통합 단축키 마스터 에이전트 (Shortcut Master Agentic RAG)

> **Ollama(Llama 3.1)**와 **LangChain** 기반의 Agentic RAG 기술을 활용하여 다양한 생산성 툴의 단축키를 스마트하게 안내하고 시각화하는 웹 서비스 MVP입니다.

<br />

## 🎯 1. 프로젝트 개요 및 문제 해결

- **생활 속 불편함**: 개발자나 디자이너, 대학생들이 실무 및 과제를 수행할 때 자주 쓰는 툴(VS Code, IntelliJ, Figma, MS Office 등)의 핵심 단축키를 매번 기억하지 못해 구글링하거나 마우스로 헤매는 비효율이 발생합니다.
- **해결 방안**:
  - 자연어로 질문하면 로컬 LLM이 상황을 스스로 판단(Agentic)하여 답변합니다.
  - 정밀한 단축키 매칭이 필요할 때만 사전에 구축된 가이드북 벡터 DB(RAG)를 동적으로 조회합니다.
  - 프론트엔드 단에서 특수 정규식을 활용해 텍스트 형태의 단축키를 입체적인 **키보드 캡(Kbd UI)** 모형으로 자동 렌더링하고, 질문 문맥에 맞춰 **우측 대시보드 탭을 실시간으로 자동 포커싱**해 주어 UI/UX 편의성을 극대화했습니다.

<br />

## 🛠️ 2. 기술 스택

- **Frontend**: React, Vite, TypeScript, Tailwind CSS
- **Backend**: Python, Flask, Flask-CORS
- **AI/RAG**: Ollama (Llama 3.1), LangChain, Chroma DB

<br />

## 📂 3. 프로젝트 구조

```text
project_root/
├── shortcuts.md           # 단축키 데이터 원본 (가이드북 마크다운)
├── step1_ingest.py        # 마크다운을 읽어 Chroma Vector DB로 빌드하는 스크립트
├── server.py              # Flask API 기반 Agentic RAG 백엔드 서버
└── frontend/              # Vite + React 프론트엔드 프로젝트 폴더
    ├── src/
    │   ├── App.tsx        # 메인 대시보드 및 챗봇 화면 UI (인터랙티브 탭 연동)
    │   ├── main.tsx       # 엔트리 포인트
    │   └── index.css      # Tailwind CSS 글로벌 스타일 주입 파일
    └── vite.config.ts     # Tailwind CSS 플러그인이 포함된 빌드 설정 파일
```

<br />

## 🚀 4. 환경 구축 및 실행 방법 (Step-by-Step)

### [사전 준비 (Prerequisites)]

본 프로젝트는 로컬 LLM 모델을 사용하므로 [Ollama 공식 홈페이지](https://ollama.com/)가 설치되어 있어야 합니다.

1. Ollama 설치 후 터미널에 아래 명령어를 입력하여 `Llama 3.1` 모델을 다운로드합니다.
   ```bash
   ollama pull llama3.1
   ```
2. 백엔드 및 RAG 데이터베이스 구축
   프로젝트 루트 폴더에서 필요한 파이썬 라이브러리들을 설치합니다.

   ```bash
   pip3 install langchain-ollama langchain-community chromadb flask flask-cors
   ```

   shortcuts.md 원본 가이드북 데이터를 읽어 로컬 벡터 데이터베이스를 생성합니다. (최초 1회만 수행, 데이터 변경 시 재실행)

   ```bash
   python3 step1_ingest.py
   ```

   실행 후 루트 폴더에 chroma_db 디렉토리가 정상적으로 생성되었는지 확인합니다.

   프론트엔드와 통신할 Flask API 서버를 구동합니다.

   ```bash
   python3 server.py
   ```

   서버가 정상 구동되면 http://127.0.0.1:5000에서 클라이언트의 요청을 대기합니다.

3. 프론트엔드 환경 세팅 및 구동
   빈 frontend 폴더 내부로 이동하여 Vite 프로젝트 초기 뼈대를 구성하고 필수 패키지를 설치합니다.

   ```bash
   cd frontend
   npm create vite@latest . -- --template react-ts
   npm install
   npm install -D tailwindcss @tailwindcss/vite
   ```

   제공된 소스 코드 파일들을 알맞은 위치에 배치합니다.

   frontend/src/App.tsx (기존 샘플 코드 전체 교체)

   frontend/vite.config.ts (TailwindCSS 플러그인 추가 확인)

   frontend/src/index.css (기존 내용 전량 삭제 후 @import "tailwindcss"; 추가)

   프론트엔드 개발 서버를 실행합니다.

   ```bash
   npm run dev
   ```

   브라우저를 열고 http://localhost:5173 주소로 접속합니다.
