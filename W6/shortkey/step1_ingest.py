from langchain_community.document_loaders import TextLoader
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

def main():
    print("shortcuts.md 읽는 중...")
    loader = TextLoader("shortcuts.md", encoding="utf-8")
    docs = loader.load()

    # 단축키 데이터는 쪼개지지 않고 한 문단이 온전히 들어가야 인식을 잘하므로 chunk_size를 약간 넉넉히 줌
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=400, chunk_overlap=40)
    splits = text_splitter.split_documents(docs)

    print("Llama 3.1 임베딩 모델 연결 및 로컬 DB 빌드 중...")
    embeddings = OllamaEmbeddings(model="llama3.1")
    vectordb = Chroma.from_documents(
        documents=splits, 
        embedding=embeddings, 
        persist_directory="./chroma_db"
    )

    print("🎉 DB 구축 완료! ./chroma_db 폴더가 생성되었습니다.")

if __name__ == "__main__":
    main()