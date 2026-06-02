from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agent.advisor import answer_question, clear_session
from api.schemas import ChatRequest, ChatResponse
from config import CHAT_MODEL, EMBEDDING_MODEL, OLLAMA_KEEP_ALIVE
from ollama_runtime import list_running_models, stop_unneeded_models


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.stopped_ollama_models = stop_unneeded_models()
    yield


app = FastAPI(title="computer_advisor_rag", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "models": {
            "chat": CHAT_MODEL,
            "embedding": EMBEDDING_MODEL,
            "keep_alive": OLLAMA_KEEP_ALIVE,
            "running": list_running_models(),
        },
        "stopped_on_startup": getattr(app.state, "stopped_ollama_models", []),
    }


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    return answer_question(request.message, request.session_id)


@app.delete("/sessions/{session_id}")
def delete_session(session_id: str):
    clear_session(session_id)
    return {"status": "cleared", "session_id": session_id}
