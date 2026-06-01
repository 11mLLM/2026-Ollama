"""
ingest.py
증상 안내 자료를 잘라서 Chroma 벡터DB에 저장한다. (최초 1회 + 자료 수정 시 실행)
실행: python ingest.py
"""
from langchain_community.document_loaders import TextLoader
from langchain_community.vectorstores import Chroma
from langchain_text_splitters import RecursiveCharacterTextSplitter

from langchain_ollama import OllamaEmbeddings

EMBED_MODEL = "nomic-embed-text"
SOURCE = "knowledge/symptoms.md"  # 지식베이스 문서
DB_DIR = "./chroma_db"            # 벡터DB 저장 위치


def main():
    docs = TextLoader(SOURCE, encoding="utf-8").load()

    # 증상 단위(## 제목)로 잘 나뉘도록 마크다운 헤더 우선 분할
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=400,
        chunk_overlap=60,
        separators=["\n## ", "\n\n", "\n", " "],
    )
    chunks = splitter.split_documents(docs)
    print(f"문서 {len(chunks)}개 조각으로 분할 완료")

    embeddings = OllamaEmbeddings(model=EMBED_MODEL)
    Chroma.from_documents(chunks, embeddings, persist_directory=DB_DIR)
    print(f"벡터DB 생성 완료 -> {DB_DIR}")


if __name__ == "__main__":
    main()