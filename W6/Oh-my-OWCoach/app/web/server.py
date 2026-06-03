"""FastAPI backend for OW-MY-COACH web GUI.

Serves the static chat frontend and exposes POST /api/chat which wraps the
Coach (query router + Agentic RAG + EXAONE). The Coach is created once at
startup so the ChromaDB connection and KB indexes are reused across requests.

Run:
    uvicorn app.web.server:app --host 127.0.0.1 --port 8000
"""
from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from app.chat.coach import Coach

STATIC_DIR = Path(__file__).resolve().parent / "static"

app = FastAPI(title="OW-MY-COACH")
_coach: Coach | None = None


def get_coach() -> Coach:
    global _coach
    if _coach is None:
        _coach = Coach()
    return _coach


@app.on_event("startup")
def _startup() -> None:
    # Warm the Coach (loads KB + connects ChromaDB) so the first request is fast.
    get_coach()


class ChatTurn(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    message: str
    role: str | None = None
    map: str | None = None
    history: list[ChatTurn] = []


class ChatResponse(BaseModel):
    answer: str
    sources: list[str]
    intent: str
    detected_role: str | None = None
    detected_map: str | None = None
    heroes: list[str] = []


@app.post("/api/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    coach = get_coach()
    history = [{"role": t.role, "content": t.content} for t in req.history]
    result = coach.ask(
        req.message,
        history=history,
        user_role=req.role or None,
        user_map=req.map or None,
    )
    route = result["route"]
    return ChatResponse(
        answer=result["answer"],
        sources=result["sources"],
        intent=route["intent"],
        detected_role=route.get("role"),
        detected_map=route.get("map"),
        heroes=route.get("heroes", []),
    )


@app.get("/")
def index() -> FileResponse:
    return FileResponse(str(STATIC_DIR / "index.html"))


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


# Static assets (css/js if any). index.html is served at "/" above.
app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")
