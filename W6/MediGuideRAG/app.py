"""
app.py
Agentic RAG 증상 → 진료과 안내 챗봇 (Streamlit + Ollama)
- 인사/잡담      -> 도구 없이 바로 답변
- 증상 관련 질문 -> 증상 가이드 벡터DB 검색 후, 가능한 원인 + 권장 진료과 + 응급신호 안내
주의: 진단 도구가 아니며 참고용임.
실행: streamlit run app.py
"""
import streamlit as st
from langchain_ollama import OllamaEmbeddings
from langchain_ollama import ChatOllama
from langchain_community.vectorstores import Chroma
from langchain_core.tools import tool
from langchain_core.messages import (
    HumanMessage, AIMessage, ToolMessage, SystemMessage,
)

CHAT_MODEL = "qwen2.5:3b"
EMBED_MODEL = "nomic-embed-text"
DB_DIR = "./chroma_db"

st.set_page_config(page_title="어디 아파요?", page_icon="🩺")
st.title("🩺 어디 아파요? — 진료과 안내 도우미")
st.caption("증상을 말하면 가능한 원인과 어느 병원·진료과로 가면 좋을지 안내해요.")
st.info("⚠️ 이 챗봇은 **진단 도구가 아닌 참고용**입니다. 정확한 진단·치료는 의료진에게 받으세요. "
        "응급 증상이면 즉시 119 또는 응급실로 가세요.")


@st.cache_resource
def load_agent():
    embeddings = OllamaEmbeddings(model=EMBED_MODEL)
    vectordb = Chroma(persist_directory=DB_DIR, embedding_function=embeddings)

    @tool
    def lookup_symptom_guide(query: str) -> str:
        """증상별 안내 자료에서 정보를 검색한다.
        사용자가 신체 증상(통증, 발열, 어지럼증, 발진, 호흡곤란 등)을 말하며
        '무슨 병일까', '어느 병원/진료과 가야 하나'를 물을 때 사용한다."""
        docs = vectordb.similarity_search(query, k=3)
        return "\n\n".join(d.page_content for d in docs) if docs else "관련 자료 없음"

    llm = ChatOllama(model=CHAT_MODEL, temperature=0.2)
    return llm.bind_tools([lookup_symptom_guide]), lookup_symptom_guide


llm_with_tools, lookup_symptom_guide = load_agent()

SYSTEM_PROMPT = """너는 증상을 듣고 '어느 진료과(병원)로 가면 좋을지' 안내하는 도우미다.

규칙:
- 인사·잡담은 도구 없이 바로 답한다.
- 사용자가 증상을 말하면 반드시 lookup_symptom_guide 도구로 자료를 찾아 답한다.
- 답변은 (1) 가능성 있는 원인(단정 금지, "~일 수 있습니다") (2) 권장 진료과
  (3) 응급 신호 해당 여부 순서로 정리한다.
- 너는 의사가 아니며, 병명을 확정하지 않는다. 약 이름이나 복용량은 알려주지 않는다.
- 답변 끝에 "정확한 진단은 의료진에게 받으세요"라고 덧붙인다.
- 자료에 없으면 지어내지 말고, 가까운 내과 또는 병원 방문을 권한다.
- 항상 한국어로 차분하고 친절하게 답한다."""

if "messages" not in st.session_state:
    st.session_state.messages = [{
        "role": "assistant",
        "content": "안녕하세요. 어디가 어떻게 불편하신가요? 증상을 자세히 말씀해 주세요. 🙂",
    }]

for m in st.session_state.messages:
    st.chat_message(m["role"]).write(m["content"])

if prompt := st.chat_input("증상을 입력하세요 (예: 어제부터 가슴이 답답하고 식은땀이 나요)"):
    st.session_state.messages.append({"role": "user", "content": prompt})
    st.chat_message("user").write(prompt)

    lc_messages = [SystemMessage(content=SYSTEM_PROMPT)]
    for m in st.session_state.messages:
        if m["role"] == "user":
            lc_messages.append(HumanMessage(content=m["content"]))
        else:
            lc_messages.append(AIMessage(content=m["content"]))

    with st.chat_message("assistant"):
        with st.spinner("증상 자료를 살펴보는 중..."):
            response = llm_with_tools.invoke(lc_messages)

            source = "🧠 일반 안내"
            if response.tool_calls:              # LLM이 자료 검색이 필요하다고 판단
                source = "📋 증상 가이드 검색"
                lc_messages.append(response)
                for tc in response.tool_calls:
                    result = lookup_symptom_guide.invoke(tc["args"])
                    lc_messages.append(
                        ToolMessage(content=result, tool_call_id=tc["id"])
                    )
                response = llm_with_tools.invoke(lc_messages)

            answer = response.content

        st.caption(f"[출처] {source}")
        st.write(answer)

    st.session_state.messages.append({"role": "assistant", "content": answer})