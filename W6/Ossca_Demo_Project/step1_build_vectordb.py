from pathlib import Path

from langchain_community.document_loaders import TextLoader
from langchain_chroma import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from config import CHROMA_DIR, EMBEDDING_MODEL, OLLAMA_KEEP_ALIVE

BASE_DIR = Path(__file__).resolve().parent
DOCS_DIR = BASE_DIR / "docs"


def load_documents():
    documents = []
    for path in sorted(DOCS_DIR.glob("*.md")):
        loader = TextLoader(str(path), encoding="utf-8")
        loaded = loader.load()
        for doc in loaded:
            doc.metadata["source"] = path.name
        documents.extend(loaded)
    return documents


def main():
    documents = load_documents()
    print(f"문서 로드 완료: {len(documents)}개")

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=700,
        chunk_overlap=100,
        separators=["\n## ", "\n### ", "\n- ", "\n\n", "\n", " "],
    )
    chunks = splitter.split_documents(documents)
    print(f"청크 분할 완료: {len(chunks)}개")

    embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL, keep_alive=OLLAMA_KEEP_ALIVE)
    Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        persist_directory=str(CHROMA_DIR),
    )
    print(f"Vector DB 저장 완료: {CHROMA_DIR}")


if __name__ == "__main__":
    main()
