# build_vector_db.py
# 개인 생활 문서를 읽고 Chroma 벡터DB로 저장하는 파일

import shutil
from pathlib import Path

from langchain_community.document_loaders import TextLoader
from langchain_community.vectorstores import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter


DATA_PATH = "data/personal_policy.md"
DB_PATH = "./chroma_db"


def main():
    # 기존 DB 삭제
    if Path(DB_PATH).exists():
        shutil.rmtree(DB_PATH)
        print("기존 chroma_db 삭제 완료")

    # 문서 로드
    loader = TextLoader(DATA_PATH, encoding="utf-8")
    documents = loader.load()

    print(f"로드된 문서 개수: {len(documents)}")

    # 문서 분할
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=400,
        chunk_overlap=80
    )

    splits = splitter.split_documents(documents)

    print(f"분할된 chunk 개수: {len(splits)}")

    # 임베딩 모델
    embeddings = OllamaEmbeddings(model="exaone3.5:7.8b")

    # 벡터DB 저장
    Chroma.from_documents(
        documents=splits,
        embedding=embeddings,
        persist_directory=DB_PATH
    )

    print("벡터DB 생성 완료")
    print(f"저장 위치: {DB_PATH}")


if __name__ == "__main__":
    main()