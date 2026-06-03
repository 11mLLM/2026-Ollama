from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
CHROMA_DIR = BASE_DIR / "chroma_db"

CHAT_MODEL = "llama3.1"
EMBEDDING_MODEL = "llama3.1"
OLLAMA_KEEP_ALIVE = 180
PROJECT_OLLAMA_MODELS = {CHAT_MODEL, EMBEDDING_MODEL}
