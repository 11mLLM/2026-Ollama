from langchain_community.document_loaders import TextLoader
from langchain_community.vectorstores import Chroma
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_ollama import OllamaEmbeddings

TARGET_FILES = [
    "PX4-user_guide/en/flight_modes/offboard.md",
    "PX4-user_guide/en/flight_modes/return.md",
    "PX4-user_guide/en/flight_modes/index.md",
    "PX4-user_guide/en/mavlink/standard_modes.md",
]

documents = []

for file_path in TARGET_FILES:
    print(f"Loading: {file_path}")

    loader = TextLoader(
        file_path,
        encoding="utf-8"
    )

    docs = loader.load()

    for doc in docs:
        cleaned_lines = []

        for line in doc.page_content.splitlines():
            line = line.strip()

            # 빈 줄 제거
            if not line:
                continue

            # 표 제거
            if line.startswith("|"):
                continue

            # markdown 표 구분선 제거
            if "|" in line and "---" in line:
                continue

            # 파라미터 표 제목 제거
            if "Parameter" in line and "Description" in line:
                continue

            cleaned_lines.append(line)

        doc.page_content = "\n".join(cleaned_lines)

    documents.extend(docs)

print(f"Loaded docs: {len(documents)}")

splitter = RecursiveCharacterTextSplitter(
    chunk_size=1500,
    chunk_overlap=200
)

chunks = splitter.split_documents(documents)

print(f"Chunks: {len(chunks)}")

embeddings = OllamaEmbeddings(
    model="nomic-embed-text"
)

vectordb = Chroma.from_documents(
    documents=chunks,
    embedding=embeddings,
    persist_directory="./chroma_db"
)

print("완료!")