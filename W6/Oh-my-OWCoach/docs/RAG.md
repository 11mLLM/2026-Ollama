# Agentic RAG: OWCS Knowledge Base

This document describes the Agentic RAG subsystem that grounds commentary
generation in real Overwatch / Overwatch Champions Series (OWCS) facts to
**prevent hallucination**. It implements FR-7 (RAG retrieval) and ARCHITECTURE
§6.7 / §10, and extends them with name grounding and agentic query routing.

## Why it exists

The VLM and EXAONE prompts already say "do not invent hero/player/team names",
but a negative instruction alone does not stop hallucination — the model has no
ground truth to anchor on. This subsystem provides that ground truth:

- a **knowledge base** of heroes, maps, league info, OWCS teams, and OWCS players;
- a **retriever** that injects only relevant, source-tagged facts into the prompt;
- a **roster grounder** that validates every VLM-extracted name against the KB and
  flags misreads (e.g. killfeed OCR turning `LIP` into `L1P`).

## Components

| File | Role |
|---|---|
| `app/rag/kb/*.jsonl` | Knowledge base documents (one JSON object per line) |
| `app/rag/knowledge_base.jsonl` | Legacy caster-style/tactic snippets (still loaded) |
| `app/rag/kb_loader.py` | Merges + de-dupes all KB docs by `id` |
| `app/rag/embeddings.py` | Ollama embedding client (`nomic-embed-text`) |
| `app/rag/build_index.py` | Embeds KB docs into a persistent ChromaDB collection |
| `app/rag/retriever.py` | Hybrid (semantic + IDF keyword) retriever with collection routing + self-query retry |
| `app/rag/roster.py` | Name validation / misread correction / canonical match roster |
| `app/vlm/scene_planner.py` | **Agentic** planner: intent + collection routing + retrieval queries |
| `app/rag/context_builder.py` | Public entrypoint: ties it all together per segment |
| `app/generation/commentary_generator.py` | Assembles the EXAONE prompt (timeline-first) |

## Collections

| Collection | Contents | Provenance |
|---|---|---|
| `hero_abilities` | All 51 OW2 heroes (14 Tank / 23 Damage / 14 Support): role, **sub-role/archetype**, ultimate, abilities (KR/EN) + roster & perks-system overview | established heroes: seed; 11 newer heroes (Venture, Hazard, Freja, Wuyang, Sierra, Domina, Mizuki, Anran, Emre, Vendetta, Jetpack Cat): cited (`source_url`) |
| `hero_perks` | All 51 heroes' Perks (2 Minor + 2 Major each) | cited per-hero (`source_url` = Liquipedia), `snapshot` tagged |
| `map_strategy` | 6 modes + 31 maps incl. all 29 competitive maps (excl. Clash) + 2 Clash | modes/older maps: seed; competitive pool: cited (`source_url`) |
| `league_info` | OWCS structure, regions, stages, events | cited (`source_url`) |
| `teams` | OWCS teams (region, placement) | cited (`source_url`) |
| `players` | OWCS players (team, role) | cited (`source_url`) |
| `league_terms` | Korean OWCS casting terminology (39 terms: 한타, 다이브, 포킹, 역다이브, 이격, 스노우볼, C9, 진영붕괴, 템포 …) | seed + Korean glossary (Namuwiki 용어 / Inven), `source_url` |
| `team_comps` | Composition archetypes (dive/brawl/poke/bunker/pirate-ship/anti-dive/GOATS) with heroes, strategy, strengths/counters — for comp-analysis commentary | seed (Namuwiki 조합 ref; page was 403 so authored from established comp theory) |
| `tactical_patterns`, `caster_style_ko` | Tactics + Korean casting style | seed |

## Setup

```bash
pip install -r requirements.txt          # includes chromadb
ollama pull nomic-embed-text             # embedding model
python -m app.rag.build_index --reset    # build the ChromaDB index
```

Verify:

```bash
python -m app.rag.kb_loader        # doc counts per collection
python -m app.rag.roster           # name validation demo
python -m app.rag.context_builder  # full per-segment RAG context demo
```

If the embedding model or ChromaDB is unavailable, the retriever **automatically
falls back to IDF keyword search** — the pipeline never crashes for lack of a
vector store.

## How "Agentic" it is

1. `scene_planner.plan_commentary` (Qwen text model, rule-based fallback) reads
   the segment and decides **commentary intent**, **which collections to query**
   (`target_collections`), and the **retrieval queries**.
2. `AgenticRetriever.retrieve` runs each query against the routed collections.
   When a query's best hit is weak, it **rewrites the query** (collection-aware
   expansion) and retries once (self-query).
3. `roster.RosterIndex` validates names and feeds misread/unknown warnings back
   into the plan's `do_not_say` list.

## Anti-hallucination mechanisms

- **Name grounding**: `actor`, `target`, `actor_hero`, `target_hero` are checked
  against the KB. Unknown → "do not assert"; close match (with digit/letter
  confusable normalization, `L1P`→`LIP`) → suggested correction.
- **Canonical match roster**: real OWCS players/teams for the match are injected
  as `MATCH_ROSTER`, so EXAONE picks names from a real list instead of inventing.
- **Timeline-first prompt order** (ARCHITECTURE §10.3): EVENT_TIMELINE >
  COMMENTARY_INTENT > MATCH_ROSTER > DO_NOT_SAY > RAG_CONTEXT. RAG docs are
  explicitly marked "reference only, not new match facts".
- **IDF keyword weighting**: a player's name outweighs boilerplate tokens shared
  across docs, so retrieval returns the *right* player, not any player.

## Data freshness & limitations

Esports rosters change frequently. Every team/player fact is tagged with a
`snapshot` (e.g. `2025_world_finals`) and a `source_url`, and the injected
roster block carries a caveat: **"스냅샷 기준 로스터입니다. 현재 라인업과 다를 수
있으므로 '현재'라고 단정하지 마세요."** Treat the seeded roster as a dated record,
not a live one.

### Updating / expanding the roster

Add lines to `app/rag/kb/players.jsonl` or `teams.jsonl` following the existing
schema (always include `snapshot` and `source_url`), then rebuild:

```bash
python -m app.rag.build_index --reset
```

### Heroes & sub-roles

All 51 playable OW2 heroes are included with a `sub_role` archetype using
Liquipedia's taxonomy: Tank = Initiator/Bruiser/Stalwart; Damage =
Sharpshooter/Flanker/Specialist/Recon; Support = Medic/Tactician/Survivor.
Sub-roles for the 11 newest heroes are sourced from their Liquipedia pages;
established heroes use the same vocabulary from general knowledge. Official
Korean names for Emre, Vendetta, and Jetpack Cat are provisional
transliterations (`hero_ko_note` flags this).

Authoritative sources used: `liquipedia.net/overwatch`,
`overwatch.blizzard.com/ko-kr/heroes`, `namu.wiki`. (The Blizzard page is a JS
SPA that WebFetch cannot render reliably — the Liquipedia per-hero pages were
the dependable extraction source and were used to confirm every roster entry.)

### Perks (per-hero, in `hero_perks`)

All 51 heroes have a perks doc (2 Minor + 2 Major) sourced per-hero from their
Liquipedia page (`source_url`), plus the `perks_system` overview in
`hero_abilities`. **Perks change every season**, so each doc carries
`snapshot: liquipedia_2026` and a `note` that perks rotate — treat them as a
dated snapshot, not permanent. To refresh, re-fetch the hero pages and rebuild.

### Maps (competitive pool)

`map_strategy` holds 6 modes + 31 maps. The 29-map **competitive pool excluding
Clash** (Control 7, Escort 8, Hybrid 7, Push 4, Flashpoint 3) is tagged
`competitive: true` with `source_url`; the 2 Clash maps are also included.

### Embedding model note

`nomic-embed-text` is the reliable default. `bge-m3` has stronger Korean
semantics but some quantizations return NaN on certain Korean inputs in current
Ollama builds; `embed_texts` detects non-finite vectors and aborts the index
build cleanly (keyword retrieval still works). To try it:

```bash
ollama pull bge-m3
OWCS_EMBED_MODEL=bge-m3 python -m app.rag.build_index --reset
```

Then raise `rag.semantic_weight` in `app/config/pipeline.yaml` toward 0.5.
