from langchain_chroma import Chroma
from langchain_core.tools import tool
from langchain_ollama import ChatOllama, OllamaEmbeddings

from config import CHAT_MODEL, CHROMA_DIR, EMBEDDING_MODEL, OLLAMA_KEEP_ALIVE

embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL, keep_alive=OLLAMA_KEEP_ALIVE)
vectordb = Chroma(
    persist_directory=str(CHROMA_DIR),
    embedding_function=embeddings,
)


def _format_docs(docs):
    if not docs:
        return "관련 문서를 찾지 못했습니다."

    formatted = []
    for index, doc in enumerate(docs, start=1):
        source = doc.metadata.get("source", "unknown")
        formatted.append(f"[문서 {index}: {source}]\n{doc.page_content}")
    return "\n\n".join(formatted)


def _search_sources(query: str, sources: list[str], k_per_source: int = 2):
    docs = []
    for source in sources:
        docs.extend(
            vectordb.similarity_search(
                query,
                k=k_per_source,
                filter={"source": source},
            )
        )
    return docs


def normalize_tool_args(args: dict | str) -> dict:
    if isinstance(args, str):
        return {"query": args}
    if "query" in args:
        return {"query": args["query"]}
    query = ", ".join(f"{key}: {value}" for key, value in args.items())
    return {"query": query}


@tool
def search_computer_buying_guide(query: str) -> str:
    """컴퓨터 구매 목적, 예산, 이동성, 사용 환경에 맞는 구매 가이드를 검색합니다."""
    docs = _search_sources(
        query,
        [
            "buying_guide.md",
            "budget_guide.md",
            "form_factor_guide.md",
            "os_ecosystem_guide.md",
        ],
    )
    return _format_docs(docs)


@tool
def search_computer_specs(query: str) -> str:
    """CPU, GPU, RAM, 저장장치, 디스플레이, 포트 등 사양 선택 기준을 검색합니다."""
    docs = _search_sources(query, ["spec_guide.md"], k_per_source=4)
    return _format_docs(docs)


@tool
def search_recommendation_policy(query: str) -> str:
    """컴퓨터 구매 상담 시 추가 질문, 추천 형식, 피해야 할 답변 기준을 검색합니다."""
    docs = _search_sources(query, ["recommendation_policy.md"], k_per_source=4)
    return _format_docs(docs)


tools = [
    search_computer_buying_guide,
    search_computer_specs,
    search_recommendation_policy,
]

llm = ChatOllama(model=CHAT_MODEL, temperature=0, keep_alive=OLLAMA_KEEP_ALIVE)
llm_with_tools = llm.bind_tools(tools)


if __name__ == "__main__":
    response = llm_with_tools.invoke("개발용 컴퓨터를 사려는데 예산은 150만원 정도야.")
    print(f"Tool 호출 여부: {response.tool_calls}")
    print(f"답변: {response.content}")
    for tool_call in response.tool_calls:
        selected_tool = {
            tool.name: tool for tool in tools
        }[tool_call["name"]]
        result = selected_tool.invoke(normalize_tool_args(tool_call["args"]))
        print(f"\nTool 결과 미리보기:\n{result[:500]}")
