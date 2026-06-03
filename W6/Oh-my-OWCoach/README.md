# Vision-to-Caster MVP

Local MVP pipeline for generating Korean Overwatch esports commentary from sampled broadcast video frames.

The default vision model is `qwen3.5:4b`, using HUD ROI crops first because full
1920x1080 frame prompts are too slow for the local MVP path.

## Quick Start

```bash
python -m app.main --video input/videos/demo.mkv --dry-run
```

Default sampling uses 10 screenshots per second across the full input video.
Pass `--max-frames N` to cap extraction during quick tests.

The full implementation will follow `docs/PLAN.md`.

## Agentic RAG (hallucination prevention)

The `app/rag/` subsystem grounds commentary in real Overwatch / OWCS facts
(heroes, maps, league, teams, players) and validates VLM-extracted names against
that knowledge base. See **`docs/RAG.md`** for details.

```bash
pip install -r requirements.txt
ollama pull nomic-embed-text
python -m app.rag.build_index --reset    # build the ChromaDB index
python -m app.rag.context_builder        # end-to-end per-segment RAG demo
```

Retrieval falls back to keyword search if the embedding model / vector store is
unavailable, so the pipeline never crashes for lack of a RAG index.
