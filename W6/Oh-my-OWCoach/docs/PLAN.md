# PLAN: Vision-to-Caster 6-Hour MVP Implementation Plan

## 1. MVP Scope Definition

**MVP goal:** 로컬 MP4에서 소수의 프레임을 추출하고, Qwen3.5-VLM으로 이벤트 후보 JSON을 만든 뒤, EXAONE으로 자연스러운 한국어 중계 문장을 생성하여 `commentary.json`, `commentary.srt`, `run_report.md`까지 저장하는 1개 happy path를 6시간 안에 완주한다.

### 문서 충돌 및 해소

- **충돌 1: PRD는 RAG, ChromaDB, 평가 루프, 20개 segment 수동 라벨링까지 포함하지만 6시간 단일 빌더 범위를 초과한다.** 이번 sprint에서는 RAG를 ChromaDB 구축이 아닌 작은 in-memory knowledge snippets로 축소하고, 정량 평가 루프는 `run_report.md`의 smoke metrics로 대체한다.
- **충돌 2: ARCHITECTURE.md는 Ollama-first를 요구하지만 Qwen3.5-VLM GGUF의 Ollama vision 지원이 불확실하다고 명시한다.** Phase 0에서 이미지 입력 smoke test를 가장 먼저 수행한다. 실패하면 6시간 MVP에서는 새 runtime을 도입하지 않고 `needs_human_review=true`가 포함된 degraded demo path를 실행하며, VLM runtime 문제를 명시적으로 보고한다.
- **충돌 3: PRD의 10분 영상, 100개 이상 screenshot, 95% JSON parse 목표는 6시간 구현/검증 범위로 과하다.** 이번 sprint는 30~90초 입력 또는 최대 12개 프레임, 3~6개 segment로 제한한다.

### In Scope: Must

- MP4 경로 입력, 영상 metadata 추출, 프레임 샘플링
- 기본 ROI crop config 및 full-frame fallback
- Qwen3.5-VLM Ollama image smoke test와 이벤트 JSON 추출
- JSON schema validation, parse retry 최대 2회
- 5~10초 segment 구성과 간단한 중복 제거
- 소형 built-in Korean style/context snippets 검색
- EXAONE Ollama 기반 한국어 중계 JSON 생성
- deterministic verifier 최소 구현
- `output/event_candidates.json`, `output/canonical_timeline.json`, `output/commentary.json`, `output/commentary.srt`, `output/run_report.md` 생성
- CLI happy path: `python -m app.main --video input/videos/demo.mp4`

### Out of Scope: Won't This Sprint

- 실시간 라이브 중계
- TTS, 음성 합성, 영상 muxing
- ChromaDB production index와 대규모 RAG corpus
- OCR 보조 모듈
- 전체 10분 영상 full batch 처리 보장
- 100개 이상 screenshot 처리 목표
- 20개 segment precision/recall 수동 평가
- commercial deployment 또는 license clearance
- human annotation UI
- Overwatch game client 또는 내부 API 연동

### MoSCoW Triage

| PRD Feature | Sprint Class | Decision |
|---|---:|---|
| FR-1 video input and metadata | Must | happy path 진입점 |
| FR-2 screenshot extraction | Must | 최대 12프레임으로 축소 |
| FR-3 HUD/ROI preprocessing | Should | 기본 crop config만 구현, 실패 시 full-frame |
| FR-4 Qwen VLM event JSON | Must | Phase 0 gate, 실패 시 degraded report |
| FR-5 event normalization/dedup | Must | 간단한 time-window dedup |
| FR-6 commentary intent planning | Must | VLM output 기반 rule-first planner |
| FR-7 RAG retrieval | Should | ChromaDB 대신 in-memory snippets |
| FR-8 EXAONE Korean commentary | Must | core output |
| FR-9 fact verification | Must | deterministic checks only |
| FR-10 output files | Must | demo artifact |
| Research metrics/evaluation | Won't | post-MVP |
| OCR/TTS/muxing/live mode | Won't | post-MVP |

## 2. Critical Path Timeline

총 시간은 **306분 구현 + 54분 buffer = 360분**이다.

| Phase | Time Box (min) | Cumulative | Tasks | Definition of Done | Risk |
|---|---:|---:|---|---|---|
| Phase 0 - Setup & model smoke gate | 45 | 45 | `ollama list`, model pull/run 확인, Qwen image input smoke, EXAONE Korean JSON smoke, sample image 준비 | 두 모델 중 EXAONE smoke는 통과하고, Qwen image 지원 여부가 `run_report.md`에 기록 가능한 상태 | High: Qwen VLM Ollama vision 미지원 가능 |
| Phase 1 - CLI skeleton and contracts | 45 | 90 | 폴더 구조, config, Pydantic/jsonschema, CLI args, output writer skeleton | `python -m app.main --video ... --dry-run`이 output 폴더와 빈 report를 만든다 | Medium: 범위 확장 유혹 |
| Phase 2 - Video ingest, sampling, ROI | 45 | 135 | OpenCV metadata, 최대 12 frame sampling, ROI crop, frame records 저장 | demo MP4에서 `data/frames/...jpg`와 crop 파일, frame manifest 생성 | Medium: codec/OpenCV issue |
| Phase 3 - Model integration + prompt tuning | 75 | 210 | Qwen event extraction, JSON repair retry, rule fallback, EXAONE prompt, Korean style tuning | 최소 3개 segment에 대해 valid event/intent/commentary JSON 생성 | High: JSON parse 실패, 한국어 문체 부자연 |
| Phase 4 - Timeline, verifier, outputs | 60 | 270 | dedup, segment builder, snippets retrieval, verifier, SRT writer, report metrics | `commentary.json`, `commentary.srt`, `canonical_timeline.json`, `run_report.md` 생성 | Medium: timestamp/SRT formatting |
| Phase 5 - End-to-end demo dry-run | 36 | 306 | clean run, one-command demo, readme/run notes, bug fixes only | fresh output 삭제 후 command 1회로 happy path 완주 | Medium: integration mismatch |
| Phase 6 - Mandatory buffer | 54 | 360 | integration/debug/polish only, scope cuts 적용 | demo command가 crash 없이 종료하고 핵심 산출물이 존재 | High: model latency |

## 3. Task Breakdown

### Must Task A: Environment and Model Gate

- [ ] `ollama list`로 local runtime 접근을 확인한다. Acceptance: command가 실패하면 setup blocker로 기록한다.
- [ ] `ollama run hf.co/LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-GGUF:Q4_K_M` smoke prompt를 실행한다. Acceptance: 한국어 JSON 또는 JSON-like 응답을 받는다.
- [ ] `ollama run hf.co/jc-builds/Qwen3.5-9B-VLM-Q4_K_M-GGUF:Q4_K_M` image smoke를 실행한다. Acceptance: 이미지 내용 기반 JSON 응답 또는 명확한 vision failure가 기록된다.
- [ ] `output/run_report.md`에 model gate 결과 필드를 정의한다. Acceptance: pass/fail/degraded 상태가 남는다.

### Must Task B: CLI and File Layout

- [ ] `app/main.py`에 `--video`, `--video-id`, `--max-frames`, `--sample-fps` 인자를 만든다. Acceptance: invalid path는 명확한 error로 종료한다.
- [ ] `app/config/pipeline.yaml` 기본값을 만든다. Acceptance: model names, frame limit, output paths가 config에서 읽힌다.
- [ ] `app/timeline/schemas.py`에 `FrameRecord`, `EventCandidate`, `SegmentTimeline`, `CommentarySegment` schema를 정의한다. Acceptance: sample dict validation이 통과한다.
- [ ] output writer를 만든다. Acceptance: JSON은 UTF-8, SRT는 valid timestamp format으로 저장된다.

### Must Task C: Video Ingest and Sampling

- [ ] OpenCV로 duration, FPS, width, height, frame count를 읽는다. Acceptance: `run_report.md`에 metadata가 기록된다.
- [ ] 최대 12개 frame을 균등 샘플링한다. Acceptance: `data/frames/{video_id}/`에 jpg가 생성된다.
- [ ] ROI config를 적용해 `top_bar`, `killfeed_right`, `lower_hud`, `center_scene` crop을 저장한다. Acceptance: crop 실패 시 full-frame만으로 계속 진행한다.
- [ ] `frame_manifest.json`을 생성한다. Acceptance: 각 frame에 `time_sec`, `image_path`, `roi_paths`가 있다.

### Must Task D: Qwen Event Extraction

- [ ] `app/config/prompts/qwen_vlm_event_extraction.md`를 만든다. Acceptance: JSON-only, no hallucination, confidence/evidence_area 규칙이 포함된다.
- [ ] Ollama client를 구현한다. Acceptance: image path와 prompt를 받아 raw response를 반환한다.
- [ ] JSON parsing과 repair retry를 구현한다. Acceptance: parse 실패 시 최대 2회 retry 후 `needs_human_review=true`로 남긴다.
- [ ] Qwen image unsupported fallback을 구현한다. Acceptance: runtime 실패 시 pipeline은 crash하지 않고 degraded event with warning을 만든다.

### Must Task E: Timeline and Intent

- [ ] event 후보를 time-window 기준으로 dedup한다. Acceptance: 같은 actor/target/event_type이 3초 안에 반복되면 하나로 합친다.
- [ ] 5~10초 segment를 구성한다. Acceptance: segment마다 start/end, canonical_events, confidence_overall이 있다.
- [ ] rule-first commentary intent planner를 만든다. Acceptance: kill event는 `first_pick` 또는 `teamfight_start`, event 없음은 `neutral_setup` 또는 `uncertain`으로 분류된다.
- [ ] `do_not_say`를 생성한다. Acceptance: low-confidence 또는 null facts는 확정 표현 금지 목록에 들어간다.

### Must Task F: Korean Commentary Generation

- [ ] `app/rag/knowledge_base.jsonl`에 최소 10개 Korean style/tactic snippets를 넣는다. Acceptance: first pick, support death, regroup, objective, ultimate caution 문체가 포함된다.
- [ ] in-memory retriever를 구현한다. Acceptance: segment당 3~5개 snippet이 반환된다.
- [ ] `app/config/prompts/exaone_commentary_generator.md`를 만든다. Acceptance: EVENT_TIMELINE 밖의 사실을 만들지 말라는 규칙이 포함된다.
- [ ] EXAONE JSON generation client를 구현한다. Acceptance: `combined_text`, `facts_used`, `generation_warnings`가 있는 valid JSON을 만든다.

### Must Task G: Verification and Outputs

- [ ] deterministic verifier를 구현한다. Acceptance: `facts_used`가 event timeline에 없는 player/hero/ability를 말하면 warning 처리한다.
- [ ] `commentary.json` writer를 완성한다. Acceptance: model_versions, segments, warnings가 포함된다.
- [ ] `commentary.srt` writer를 완성한다. Acceptance: subtitle index, `HH:MM:SS,mmm` timestamp, Korean text가 출력된다.
- [ ] `run_report.md` writer를 완성한다. Acceptance: model gate, frame count, parse success rate, verifier warning count, degraded 여부가 기록된다.
- [ ] fresh demo run을 실행한다. Acceptance: `python -m app.main --video input/videos/demo.mp4 --max-frames 12`가 exit code 0으로 끝난다.

## 4. Dependencies & Sequencing

- Ollama runtime 접근이 Phase 0의 blocker다.
- EXAONE model smoke가 실패하면 commentary generation이 불가능하므로 MVP는 중단하고 setup issue로 기록한다.
- Qwen image input smoke는 가장 큰 risk다. 실패해도 architecture에 명시된 불확실성이므로 pipeline은 degraded mode로 끝까지 실행하되, vision extraction은 demo pass로 주장하지 않는다.
- Frame sampling은 OpenCV 또는 ffmpeg 기반이다. OpenCV codec 실패 시 `ffmpeg` CLI로 frame extraction만 대체한다.
- EXAONE prompt tuning은 canonical timeline schema가 안정된 뒤에만 진행한다.
- SRT output은 commentary segment의 start/end가 확정된 뒤에 작성한다.

## 5. Scope-Cut Ladder

1. **T+45min에서 Qwen image smoke가 실패하면:** llama.cpp 등 새 runtime 도입은 하지 않고, Qwen 단계는 degraded warning으로 고정한다.
2. **T+90min에서 skeleton이 늦으면:** ROI crop을 Phase 2에서 제외하고 full-frame만 사용한다.
3. **T+135min에서 video sampling이 늦으면:** 입력 범위를 30초 또는 최대 6프레임으로 줄인다.
4. **T+210min에서 model JSON parse가 불안정하면:** Qwen output은 raw text와 fallback event로 저장하고, EXAONE 입력은 validated minimal event schema만 사용한다.
5. **T+270min에서 verifier가 늦으면:** LLM verifier는 완전히 제외하고 deterministic field checks만 남긴다.
6. **T+306min에서 demo가 불안정하면:** RAG snippets 검색을 제거하고 fixed Korean style preset만 사용한다.
7. **Buffer 30분 이상 소진 시:** polish, extra metrics, extra prompts, additional sample video support를 모두 중단한다.

## 6. Demo Readiness Checklist

- [ ] `python -m app.main --video input/videos/demo.mp4 --max-frames 12` 실행 command가 문서화되어 있다.
- [ ] demo input MP4가 없을 때 명확한 error와 expected path를 보여준다.
- [ ] frame extraction 결과가 `data/frames/`에 생성된다.
- [ ] Qwen VLM gate 결과가 `run_report.md`에 pass/fail/degraded로 기록된다.
- [ ] EXAONE이 한국어 `combined_text`를 생성한다.
- [ ] `commentary.json`이 valid JSON이다.
- [ ] `commentary.srt`가 subtitle player에서 열 수 있는 형식이다.
- [ ] 한국어 문장이 과도한 번역투이거나 깨진 문자 없이 읽힌다.
- [ ] event timeline에 없는 ultimate, player, hero, team claim이 verifier warning으로 잡힌다.
- [ ] main flow가 crash 없이 종료된다.

## 7. Deferred Backlog

- ChromaDB 기반 persistent RAG index
- `nomic-embed-text` 또는 `bge-m3` embedding integration
- OCR-assisted killfeed extraction
- adaptive sampling and highlight detection
- `llama.cpp server --mmproj` VLM fallback runtime
- 10분 영상 batch 처리와 throughput tuning
- 20개 segment human evaluation workflow
- precision/recall 측정용 annotation set
- audio intensity feature extraction
- TTS voice generation
- video subtitle muxing
- web UI or annotation UI
- multiple broadcast ROI profiles
- commercial license review and deployment packaging
