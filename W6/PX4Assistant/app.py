import time
import streamlit as st

from langchain_community.vectorstores import Chroma
from langchain_ollama import OllamaEmbeddings
from langchain_ollama import ChatOllama

# ----------------------------
# 페이지 설정
# ----------------------------

st.set_page_config(
    page_title="PX4 Assistant",
    page_icon="🚁",
    layout="wide"
)

st.title("🚁 PX4 Assistant")
st.caption("PX4 공식 문서 기반 RAG 챗봇")

# ----------------------------
# 리소스 로딩
# ----------------------------

@st.cache_resource
def load_db():
    embeddings = OllamaEmbeddings(
        model="nomic-embed-text"
    )

    vectordb = Chroma(
        persist_directory="./chroma_db",
        embedding_function=embeddings
    )

    return vectordb


@st.cache_resource
def load_llm():
    return ChatOllama(
        model="qwen2.5-coder:3b",
        temperature=0
    )


vectordb = load_db()
llm = load_llm()

# ----------------------------
# PX4 키워드
# ----------------------------

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
    "failsafe",
]

# ----------------------------
# 채팅 기록
# ----------------------------

if "messages" not in st.session_state:
    st.session_state.messages = []

# ----------------------------
# 이전 대화 출력
# ----------------------------

for msg in st.session_state.messages:

    with st.chat_message(msg["role"]):

        st.markdown(msg["content"])

        if msg.get("sources"):

            with st.expander("출처 보기"):

                for source in msg["sources"]:
                    st.code(source)

        if msg.get("time"):
            st.caption(f"응답시간: {msg['time']:.1f}초")

# ----------------------------
# 입력창
# ----------------------------

question = st.chat_input(
    "PX4에 대해 질문해보세요..."
)

# ----------------------------
# 질문 처리
# ----------------------------

if question:

    # 사용자 메시지 저장
    st.session_state.messages.append(
        {
            "role": "user",
            "content": question
        }
    )

    with st.chat_message("user"):
        st.markdown(question)

    start_time = time.time()

    question_lower = question.lower()

    need_rag = any(
        keyword in question_lower
        for keyword in PX4_KEYWORDS
    )

    with st.chat_message("assistant"):

        with st.spinner("생각 중..."):

            # ------------------------
            # 일반 질문
            # ------------------------

            if not need_rag:

                response = llm.invoke(question)

                answer = response.content

                elapsed = time.time() - start_time

                st.markdown(answer)
                st.caption(f"응답시간: {elapsed:.1f}초")

                st.session_state.messages.append(
                    {
                        "role": "assistant",
                        "content": answer,
                        "time": elapsed
                    }
                )

            # ------------------------
            # PX4 질문
            # ------------------------

            else:

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
- 답변은 한국어로 작성하세요.
- 핵심만 간결하게 설명하세요.
- 3문장 이내로 답변하세요.

문서:
{context}

질문:
{question}

답변:
"""

                response = llm.invoke(prompt)

                answer = response.content

                elapsed = time.time() - start_time

                sources = sorted(
                    set(
                        doc.metadata["source"]
                        for doc in docs
                    )
                )

                st.markdown(answer)

                with st.expander("출처 보기"):

                    for source in sources:
                        st.code(source)

                st.caption(f"응답시간: {elapsed:.1f}초")

                st.session_state.messages.append(
                    {
                        "role": "assistant",
                        "content": answer,
                        "sources": sources,
                        "time": elapsed
                    }
                )

# ----------------------------
# 사이드바
# ----------------------------

with st.sidebar:

    st.header("PX4 Assistant")

    st.markdown(
        """
### 사용 기술

- Ollama
- Qwen2.5-Coder 3B
- Nomic Embed Text
- ChromaDB
- Streamlit

### 예시 질문

- Offboard Mode가 뭐야?
- Return Mode는 언제 사용해?
- MAVLink는 무엇인가?
- PX4란 무엇인가?
"""
    )

    if st.button("대화 초기화"):

        st.session_state.messages = []

        st.rerun()