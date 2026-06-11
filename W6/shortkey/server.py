from flask import Flask, request, jsonify
from flask_cors import CORS
from langchain_ollama import ChatOllama
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import OllamaEmbeddings
from langchain_core.tools import tool
from langchain_core.messages import HumanMessage, ToolMessage, SystemMessage

app = Flask(__name__)
CORS(app)  # 프론트엔드 포트 번호가 달라도 통신이 가능하게 설정 (CORS 에러 방지)

# ── 로컬 DB 로드 및 셋업
embeddings = OllamaEmbeddings(model="llama3.1")
vectordb = Chroma(persist_directory="./chroma_db", embedding_function=embeddings)

@tool
def query_shortcut_guide(query: str) -> str:
    """VS Code, Figma, Chrome 등 개발 및 디자인 툴의 단축키 정보를 검색합니다.
    특정 기능의 단축키나 키 조합을 알고 싶을 때 사용하세요.
    """
    docs = vectordb.similarity_search(query, k=2)
    return "\n\n".join([d.page_content for d in docs]) if docs else "관련 단축키 없음"

llm = ChatOllama(model="llama3.1", temperature=0)
llm_with_tools = llm.bind_tools([query_shortcut_guide])

SYSTEM_PROMPT = """당신은 개발 및 디자인 툴 단축키 안내 요정입니다.
- 단축키 사용 팁이나 효용성 같은 일반 대화는 절대 도구를 쓰지 말고 자체 답변하세요.
- 특정 기능에 대한 단축키 조합을 물어볼 때만 반드시 query_shortcut_guide 도구를 사용하세요.
- 답변은 무조건 한국어로 하세요.
- 답변 포맷 예시: 
  [VS Code] 멀티 커서 단축키입니다.
  - Mac: Cmd + Option + Down
  - Windows: Ctrl + Alt + Down
  정확한 단축키명을 명시해 줘야 웹 브라우저 UI에서 파싱할 수 있습니다."""

@app.route("/api/chat", methods=["POST"])
def chat():
    data = request.json
    user_input = data.get("message", "").strip()
    
    if not user_input:
        return jsonify({"error": "Message is empty"}), 400

    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        HumanMessage(content=user_input)
    ]

    # LLM 1차 판별 (자체 대화냐, 툴 호출이냐)
    response = llm_with_tools.invoke(messages)
    tool_called = False

    if response.tool_calls:
        tool_called = True
        for tool_call in response.tool_calls:
            # 파이썬 내부에서 RAG 검색 도구 실행
            tool_result = query_shortcut_guide.invoke(tool_call["args"])
            
            # 검색 결과를 메시지 히스토리에 추가
            messages.append(response)
            messages.append(ToolMessage(content=tool_result, tool_call_id=tool_call["id"]))
        
        # 데이터를 추가로 주입받은 LLM이 최종 답변 생성
        final_response = llm_with_tools.invoke(messages)
        answer = final_response.content
    else:
        answer = response.content

    # 프론트엔드가 UI를 분기 처리할 수 있게 '툴 사용 여부'도 함께 넘겨줌
    return jsonify({
        "answer": answer,
        "tool_called": tool_called
    })

if __name__ == "__main__":
    app.run(port=5005, debug=True)