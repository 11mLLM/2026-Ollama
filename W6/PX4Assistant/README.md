\# PX4 Assistant 🚁



PX4 공식 문서를 기반으로 질문에 답변하는 RAG(Retrieval-Augmented Generation) 챗봇입니다.



\## 프로젝트 소개



PX4 공식 문서는 방대하기 때문에 원하는 정보를 찾는 데 시간이 걸립니다.



이 프로젝트는 PX4 문서를 벡터 데이터베이스에 저장한 뒤, 사용자의 질문과 관련된 문서를 검색하여 답변을 생성합니다.



모든 기능은 로컬 환경에서 동작하며 API Key가 필요하지 않습니다.



\## 기술 스택



\* Ollama

\* Qwen2.5-Coder 3B

\* Nomic Embed Text

\* ChromaDB

\* Streamlit

\* LangChain



\## 실행 방법



\### 1. PX4 문서 다운로드



```bash

git clone https://github.com/PX4/PX4-user\_guide.git

```



\### 2. 모델 다운로드



```bash

ollama pull qwen2.5-coder:3b

ollama pull nomic-embed-text

```



\### 3. 패키지 설치



```bash

pip install -r requirements.txt

```



\### 4. Vector DB 생성



```bash

python build\_db.py

```



\### 5. 실행



```bash

streamlit run app.py

```



\## 예시 질문



\* Offboard Mode가 뭐야?

\* Return Mode는 언제 사용해?

\* MAVLink는 무엇인가?



\## 프로젝트 구조



```text

app.py          # Streamlit UI

build\_db.py     # 문서 임베딩 및 DB 생성

chat.py         # CLI 버전 챗봇

```



