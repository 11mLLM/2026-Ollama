from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    session_id: str | None = None
    message: str = Field(..., min_length=1)


class ModelInfo(BaseModel):
    chat: str
    embedding: str


class ToolCallInfo(BaseModel):
    name: str
    args: dict
    result_preview: str
    supplemental: bool


class ChatResponse(BaseModel):
    session_id: str
    answer: str
    tool_calls: list[ToolCallInfo]
    sources: list[str]
    history_size: int
    constraints: dict = {}
    model: ModelInfo
