# rag.py
# ChromaDB에 저장된 개인 문서를 검색하는 모듈

from langchain_community.vectorstores import Chroma
from langchain_ollama import OllamaEmbeddings


DB_PATH = "./chroma_db"
EMBEDDING_MODEL = "exaone3.5:7.8b"


def load_vector_db():
    """저장된 Chroma 벡터DB를 불러온다."""
    embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL)

    vectordb = Chroma(
        persist_directory=DB_PATH,
        embedding_function=embeddings
    )

    return vectordb


def search_personal_docs(query: str, k: int = 3) -> str:
    """
    개인 생활 문서에서 질문과 관련된 내용을 검색한다.

    Args:
        query: 사용자 질문
        k: 가져올 문서 chunk 개수

    Returns:
        검색된 문서 내용 문자열
    """
    vectordb = load_vector_db()
    docs = vectordb.similarity_search(query, k=k)

    if not docs:
        return "관련 문서를 찾지 못했습니다."

    return "\n\n".join([doc.page_content for doc in docs])


if __name__ == "__main__":
    # 단독 실행 테스트
    test_query = "교수님께 메일 보낼 때 어떻게 써야 해?"
    result = search_personal_docs(test_query)

    print("[검색 질문]")
    print(test_query)

    print("\n[검색 결과]")
    print(result)