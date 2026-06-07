# Ollama Agentic RAG Learning Notes

작성자: myhousemouse

## 학습 목표

- Ollama를 사용해 로컬 환경에서 LLM을 실행하는 흐름을 정리한다.
- LangChain, ChromaDB, Ollama Embeddings를 이용한 RAG 구조를 이해한다.
- Tool Calling이 Agentic RAG 안에서 어떤 역할을 하는지 정리한다.

## Ollama 기본 명령어

Ollama 서버와 모델 상태는 아래 명령어로 확인할 수 있다.

```bash
curl http://localhost:11434/api/tags
ollama ls
```

실습에서는 `llama3.1` 모델을 사용했다.

```bash
ollama pull llama3.1
```

## RAG를 사용하는 이유

RAG는 모델의 기본 지식만으로 답변하기 어려운 내용을 외부 문서에서 검색해 보완하는 방식이다. 실습을 통해 RAG가 필요한 이유를 세 가지 관점에서 이해했다.

- 비용: 모든 정보를 긴 프롬프트로 넣으면 토큰 비용이 커진다.
- 속도: 필요한 문서만 검색해 넣으면 긴 컨텍스트를 매번 전달하는 것보다 효율적이다.
- 보안: 내부 문서를 모델 학습 데이터로 직접 넣지 않고 검색 대상으로 분리할 수 있다.

## Agentic RAG 흐름

Agentic RAG는 LLM이 먼저 질문을 판단하고, 필요한 경우에만 검색 도구를 호출하는 구조다.

1. 사용자가 질문한다.
2. LLM이 자체 지식으로 답할 수 있는지 판단한다.
3. 회사 규정, 정책, 문서 기반 질문이면 검색 Tool을 호출한다.
4. 애플리케이션 코드가 Tool을 실제로 실행한다.
5. 검색 결과를 다시 LLM에 전달해 최종 답변을 생성한다.

중요한 점은 LLM이 함수를 직접 실행하지 않는다는 것이다. LLM은 함수 설명을 보고 어떤 도구를 호출할지와 어떤 인자를 넘길지를 생성하고, 실제 실행은 애플리케이션이 담당한다.

## 실습 코드 요약

LangChain에서는 `@tool`로 검색 함수를 정의하고, `bind_tools`로 LLM에 도구 목록을 연결할 수 있다.

```python
from langchain_core.tools import tool
from langchain_ollama import ChatOllama

@tool
def query_company_policy(query: str) -> str:
    """회사 규정 문서에서 관련 내용을 검색합니다."""
    docs = vectordb.similarity_search(query, k=3)
    if not docs:
        return "관련 문서를 찾지 못했습니다."
    return "\n\n".join([doc.page_content for doc in docs])

llm = ChatOllama(model="llama3.1", temperature=0)
llm_with_tools = llm.bind_tools([query_company_policy])
```

도구 호출이 필요한 질문과 일반 대화를 분리하려면 System Prompt에서 도구 사용 조건을 명확히 작성하는 것이 중요했다.

```python
SYSTEM_PROMPT = """당신은 회사 내부 규정 안내 어시스턴트입니다.

반드시 아래 규칙을 따르세요:
- 인사, 날씨, 일상적인 대화는 절대 도구를 사용하지 말고 바로 답변하세요.
- 회사 정책, 복리후생, 휴가, 보안 규정처럼 회사 문서가 필요한 질문에만
  query_company_policy 도구를 사용하세요.
- 답변은 항상 한국어로 하세요."""
```

## 배운 점

- 로컬 LLM은 API 사용과 달리 모델 설치, 실행 상태 확인, 모델 선택 과정을 직접 관리해야 한다.
- RAG는 단순히 문서를 붙여 넣는 방식이 아니라 문서 로딩, 청크 분할, 임베딩, 벡터 검색, 답변 생성이 연결된 구조다.
- Tool Calling은 LLM 애플리케이션에서 조건부 기능 실행을 가능하게 한다.
- 좋은 Agentic RAG를 만들려면 Tool 설명과 System Prompt를 구체적으로 작성해야 한다.

## 다음에 해볼 일

- 개인 학습 문서를 벡터 DB에 넣고 질의응답 챗봇을 만들어본다.
- 검색 결과가 없을 때의 예외 처리와 답변 품질 개선 방법을 실험한다.
- PR/Issue 템플릿을 사용해 실습 결과와 개선 사항을 더 체계적으로 기록한다.
