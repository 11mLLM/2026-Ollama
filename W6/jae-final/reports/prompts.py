"""Prompt templates for summary and RAG answers."""

from __future__ import annotations

from .discovery import ReportGroup, Selection, rel


def fenced(language: str, text: str) -> str:
    return f"````{language}\n{text.rstrip()}\n````"


def trim_text(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    marker = "\n\n[TRUNCATED: 입력이 --max-input-chars 제한을 초과해 뒷부분을 생략했습니다.]"
    return text[: max(0, max_chars - len(marker))].rstrip() + marker


def summary_prompt(selection: Selection, report_context: str) -> str:
    return f"""당신은 Home Search 프로젝트의 개발 보조 도구입니다.
아래 Codex 작업 보고서만 근거로 한국어 Markdown 요약을 작성하세요.

규칙:
- 보고서에 없는 내용은 추정하지 말고 `보고서에 없음`이라고 쓰세요.
- 완료, 실패, Partial, skipped, not run 상태를 구분하세요.
- public API, DB, ingest, frontend, infra 영향이 보이면 따로 드러내세요.
- 검증 명령은 pass/fail/not run/skipped를 유지해서 요약하세요.
- 출력은 간결하되 다음에 이어서 작업할 사람이 바로 판단할 수 있어야 합니다.

반드시 아래 heading 순서로만 작성하세요:
# Codex 작업 보고서 요약
## 핵심 요약
## 완료된 작업
## 실패/부분 완료/차단
## 검증 근거
## 계약/데이터 영향
## 잔여 위험
## 다음 행동
## 분석 대상

선택 기준:
- reports_dir: {rel(selection.reports_dir, selection.project_root)}
- selection: {selection.reason}
- report_groups: {len(selection.groups)}

보고서 원문:
{report_context}
"""


def batch_prompt(group: ReportGroup, report_context: str) -> str:
    return f"""당신은 Home Search 프로젝트의 개발 보조 도구입니다.
아래 단일 Codex 작업 보고서 그룹을 한국어 Markdown으로 중간 요약하세요.
보고서에 없는 내용은 추정하지 말고 `보고서에 없음`이라고 쓰세요.

필수 항목:
- 상태
- 작업 내용
- 검증 근거
- 계약/데이터 영향
- 잔여 위험
- 다음 행동
- 분석 파일

작업 그룹: {group.slug}

보고서 원문:
{report_context}
"""


def final_batch_prompt(selection: Selection, intermediate_summaries: str) -> str:
    return f"""당신은 Home Search 프로젝트의 개발 보조 도구입니다.
아래 중간 요약들만 근거로 전체 Codex 작업 보고서 요약을 한국어 Markdown으로 작성하세요.
보고서에 없는 내용은 추정하지 말고 `보고서에 없음`이라고 쓰세요.

반드시 아래 heading 순서로만 작성하세요:
# Codex 작업 보고서 요약
## 핵심 요약
## 완료된 작업
## 실패/부분 완료/차단
## 검증 근거
## 계약/데이터 영향
## 잔여 위험
## 다음 행동
## 분석 대상

선택 기준:
- reports_dir: {rel(selection.reports_dir, selection.project_root)}
- selection: {selection.reason}
- report_groups: {len(selection.groups)}

중간 요약:
{intermediate_summaries}
"""


def answer_prompt(question: str, context: str) -> str:
    return f"""당신은 Home Search 프로젝트의 Codex 작업 보고서 RAG 도우미입니다.
아래 검색 근거만 사용해 사용자의 질문에 한국어 Markdown으로 답하세요.

규칙:
- 검색 근거에 없는 내용은 추정하지 말고 `검색 근거에 없음`이라고 쓰세요.
- 가능한 경우 관련 보고서 경로와 작업 slug를 함께 언급하세요.
- 실패, Partial, skipped, not run 상태는 구분하세요.
- 답변은 다음 작업자가 바로 판단할 수 있게 간결하게 작성하세요.

반드시 아래 heading 순서로만 작성하세요:
# 답변
## 요약
## 근거
## 관련 보고서
## 불확실한 점
## 다음 액션

사용자 질문:
{question}

검색 근거:
{context}
"""
