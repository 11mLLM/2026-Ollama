import time

from langchain_community.vectorstores import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_ollama import ChatOllama

embeddings = OllamaEmbeddings(
    model="nomic-embed-text"
)

vectordb = Chroma(
    persist_directory="./chroma_db",
    embedding_function=embeddings
)

llm = ChatOllama(
    model="qwen2.5-coder:3b",
    temperature=0
)

PX4_KEYWORDS = [
    "px4",
    "offboard",
    "return",
    "rtl",
    "mavlink",
    "mission",
    "flight mode",
    "flight modes",
    "qgroundcontrol",
    "drone",
    "uav",
    "ros2",
    "failsafe"
]

while True:
    question = input("\n질문: ").strip()

    if not question:
        continue

    if question.lower() in ["exit", "quit"]:
        break

    start_time = time.time()

    question_lower = question.lower()

    need_rag = any(
        keyword in question_lower
        for keyword in PX4_KEYWORDS
    )

    # 일반 질문
    if not need_rag:
        response = llm.invoke(question)

        elapsed = time.time() - start_time

        print("\n답변:")
        print(response.content)
        print(f"\n응답시간: {elapsed:.1f}초")

        continue

    # PX4 질문
    docs = vectordb.max_marginal_relevance_search(
        question,
        k=2
    )

    context = "\n\n".join(
        doc.page_content[:500]
        for doc in docs
    )

    prompt = f"""
당신은 PX4 공식 문서 QA 시스템입니다.

규칙:
- 반드시 문서 내용만 사용하세요.
- 추측하지 마세요.
- 문서에 없는 내용은 "문서에서 찾을 수 없습니다."라고 답하세요.
- 답변은 3문장 이내로 작성하세요.
- 불필요한 설명을 하지 마세요.

문서:
{context}

질문:
{question}

답변:
"""

    response = llm.invoke(prompt)

    elapsed = time.time() - start_time

    print("\n답변:")
    print(response.content)

    sources = set()

    for doc in docs:
        sources.add(doc.metadata["source"])

    print("\n출처:")
    for source in sorted(sources):
        print(f"- {source}")

    print(f"\n응답시간: {elapsed:.1f}초")