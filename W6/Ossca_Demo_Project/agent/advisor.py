import re
from typing import Any
from uuid import uuid4

from langchain_chroma import Chroma
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import tool
from langchain_ollama import ChatOllama, OllamaEmbeddings

from config import CHAT_MODEL, CHROMA_DIR, EMBEDDING_MODEL, OLLAMA_KEEP_ALIVE
from product_tools import recommend_pc_components


MAX_HISTORY_MESSAGES = 8
DEFAULT_SESSION_ID = "default"
SESSION_MESSAGES: dict[str, list[HumanMessage | AIMessage]] = {}
SESSION_CONSTRAINTS: dict[str, dict[str, Any]] = {}
OUT_OF_SCOPE_MESSAGE = "제가 답변드릴 수 있는 영역이 아닙니다."
SCOPE_KEYWORDS = [
    "컴퓨터",
    "pc",
    "데스크탑",
    "데스크톱",
    "노트북",
    "미니",
    "맥미니",
    "맥 미니",
    "cpu",
    "gpu",
    "ram",
    "램",
    "ssd",
    "저장장치",
    "그래픽",
    "사양",
    "조립",
    "개발",
    "코딩",
    "container",
    "컨테이너",
    "docker",
    "도커",
    "리눅스",
    "linux",
    "게임",
    "영상편집",
    "영상 편집",
    "3d",
    "ai",
    "머신러닝",
    "주식",
    "주식거래",
    "트레이딩",
    "증권",
    "hts",
    "mts",
    "차트",
]
FOLLOW_UP_KEYWORDS = [
    "램은",
    "ram은",
    "충분",
    "안하",
    "안 하",
    "쓸거",
    "쓸 거",
    "사용",
    "우분투",
    "ubuntu",
    "윈도우",
    "windows",
    "맥",
    "mac",
]

embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL, keep_alive=OLLAMA_KEEP_ALIVE)
vectordb = Chroma(
    persist_directory=str(CHROMA_DIR),
    embedding_function=embeddings,
)


def _format_docs(docs):
    if not docs:
        return "관련 문서를 찾지 못했습니다."

    formatted = []
    for index, doc in enumerate(docs, start=1):
        source = doc.metadata.get("source", "unknown")
        formatted.append(f"[문서 {index}: {source}]\n{doc.page_content}")
    return "\n\n".join(formatted)


def _extract_sources(text: str) -> list[str]:
    sources = []
    for line in text.splitlines():
        if line.startswith("[문서 ") and ": " in line and line.endswith("]"):
            sources.append(line.rsplit(": ", 1)[1][:-1])
    return sources


def _search_sources(query: str, sources: list[str], k_per_source: int = 2):
    docs = []
    for source in sources:
        docs.extend(
            vectordb.similarity_search(
                query,
                k=k_per_source,
                filter={"source": source},
            )
        )
    return docs


def normalize_tool_args(args: dict | str) -> dict:
    if isinstance(args, str):
        return {"query": args}
    if "query" in args:
        return {"query": args["query"]}
    query = ", ".join(f"{key}: {value}" for key, value in args.items())
    return {"query": query}


@tool
def search_computer_buying_guide(query: str) -> str:
    """컴퓨터 구매 목적, 예산, 이동성, 사용 환경에 맞는 구매 가이드를 검색합니다."""
    docs = _search_sources(
        query,
        [
            "buying_guide.md",
            "budget_guide.md",
            "form_factor_guide.md",
            "os_ecosystem_guide.md",
        ],
    )
    return _format_docs(docs)


@tool
def search_computer_specs(query: str) -> str:
    """CPU, GPU, RAM, 저장장치, 디스플레이, 포트 등 사양 선택 기준을 검색합니다."""
    docs = _search_sources(query, ["spec_guide.md"], k_per_source=4)
    return _format_docs(docs)


@tool
def search_recommendation_policy(query: str) -> str:
    """컴퓨터 구매 상담 시 추가 질문, 추천 형식, 피해야 할 답변 기준을 검색합니다."""
    docs = _search_sources(query, ["recommendation_policy.md"], k_per_source=4)
    return _format_docs(docs)


TOOLS = {
    "search_computer_buying_guide": search_computer_buying_guide,
    "search_computer_specs": search_computer_specs,
    "search_recommendation_policy": search_recommendation_policy,
    "recommend_pc_components": recommend_pc_components,
}

llm = ChatOllama(model=CHAT_MODEL, temperature=0, keep_alive=OLLAMA_KEEP_ALIVE)
llm_with_tools = llm.bind_tools(list(TOOLS.values()))

SYSTEM_PROMPT = """당신은 컴퓨터 구매 상담 어시스턴트입니다.

반드시 아래 규칙을 따르세요:
- 답변은 항상 한국어로 하세요.
- 인사, 잡담, 프로젝트와 무관한 질문에는 도구를 사용하지 말고 짧게 답하세요.
- 컴퓨터 구매, 사양, 예산, 데스크탑/미니 PC/노트북 선택 질문에는 필요한 검색 도구를 사용하세요.
- 추천을 확정해야 하는 질문은 구매 가이드와 사양 기준을 함께 확인하세요.
- CPU, GPU, RAM, 저장장치 실 제품 후보가 필요한 질문에는 recommend_pc_components 도구를 사용하세요.
- 사용 목적, 예산, 이동성 중 핵심 정보가 부족하면 추천을 확정하지 말고 먼저 추가 질문을 하세요.
- 최신 제품 가격이나 재고를 확인한 것처럼 말하지 마세요. 가격은 변동 가능한 범위로 설명하세요.
- 검색 결과에 없는 구체적인 CPU/GPU 제품명은 예시로 들지 마세요.
- 소형 데스크탑은 공간 절약형이지 휴대용 컴퓨터가 아닙니다.
- 추천 답변은 가능하면 다음 구조를 따르세요:
  1. 추천 형태
  2. 권장 사양
  3. 예상 가격대
  4. 추천 이유
  5. 대안
  6. 추가 확인 질문
"""


def _final_instruction(
    user_input: str,
    product_results: list[str],
    constraints: dict[str, Any],
) -> HumanMessage:
    product_catalog = "\n\n".join(product_results).strip()
    if not product_catalog:
        product_catalog = "제품 카탈로그 검색 결과 없음"

    special_rules = []
    if constraints.get("gpu_requirement") == "전용 GPU 불필요":
        special_rules.append(
            "전용 GPU가 불필요한 조건입니다. GPU 항목에는 "
            "'전용 GPU 불필요, 내장 그래픽 또는 기본 출력용 그래픽으로 충분'이라고 쓰고, "
            "RTX/Radeon 같은 전용 GPU를 기본 추천에 넣지 마세요."
        )
    if constraints.get("exclude"):
        special_rules.append(
            f"사용자가 제외한 작업({constraints['exclude']})은 추천 이유나 추가 질문에 다시 포함하지 마세요."
        )
    if constraints.get("purpose") and "주식거래" in constraints["purpose"]:
        special_rules.append(
            "주식거래 용도는 고성능 GPU가 중요한 작업이 아닙니다. "
            "추천 이유와 추가 질문은 RAM, SSD, 화면 크기/해상도, 외부 모니터 연결, 안정성, 휴대성을 중심으로 작성하세요."
        )
    if constraints.get("form_factor") == "노트북":
        special_rules.append(
            "노트북은 CPU/GPU를 개별 부품으로 추천하지 말고 완제품의 '탑재 사양'으로 표현하세요. "
            "데스크탑용 CPU/GPU 제품명은 노트북 기본 추천에 쓰지 마세요. "
            "CPU는 '중급 모바일 CPU 탑재 모델', GPU는 목적에 따라 '내장 그래픽으로 충분'처럼 표현하세요."
        )
    if constraints.get("budget"):
        special_rules.append(
            f"예상 가격대는 사용자가 말한 예산({constraints['budget']}) 기준으로 작성하세요. "
            "100만원을 100,000원처럼 잘못 변환하지 마세요."
        )

    special_rule_text = "\n".join(f"- {rule}" for rule in special_rules)
    if not special_rule_text:
        special_rule_text = "- 없음"

    return HumanMessage(
        content=(
            "아래 정보만 근거로 사용자의 질문에 대한 최종 답변을 작성하세요.\n\n"
            f"[원 질문]\n{user_input}\n\n"
            f"[현재까지 확정된 사용자 조건]\n{_constraints_to_text(constraints)}\n\n"
            "[대화 맥락 처리 규칙]\n"
            "- 이전 대화에서 확정된 예산, 폼팩터, 운영체제, 사용 목적, 이동성 조건을 유지하세요.\n"
            "- 사용자가 추가 확인 질문에 답한 경우, 새 요구사항이 아니라 기존 추천 조건을 보완하는 정보로 해석하세요.\n"
            "- 이전 예산을 초과하는 구성은 기본 추천이 아니라 '상향 대안'으로만 표시하세요.\n"
            "- 전체 컴퓨터 기준 가격대만 말하고, 부품별 가격은 추정하지 마세요.\n\n"
            "[특수 제약]\n"
            f"{special_rule_text}\n\n"
            "[제품 카탈로그 결과]\n"
            f"{product_catalog}\n\n"
            "[제품명 사용 규칙]\n"
            "- CPU, GPU, RAM, 저장장치 제품명은 제품 카탈로그 결과에 있는 이름만 그대로 사용하세요.\n"
            "- 카탈로그에 없는 제품명, 구형 예시 제품명, 임의 브랜드명은 쓰지 마세요.\n"
            "- 단, 노트북 추천에서는 데스크탑 부품명을 억지로 쓰지 말고 탑재 사양 기준으로 설명하세요.\n"
            "- 소형 데스크탑은 공간 절약형 대안이지 휴대용 컴퓨터가 아닙니다.\n\n"
            "[답변 형식]\n"
            "1. 추천 형태\n"
            "2. 추천 부품 조합\n"
            "   - CPU\n"
            "   - GPU\n"
            "   - RAM\n"
            "   - 저장장치\n"
            "3. 예상 가격대\n"
            "4. 추천 이유\n"
            "5. 호환성/구매 주의사항\n"
            "6. 대안\n"
            "7. 추가 확인 질문\n"
        )
    )


def _get_recent_history(session_id: str) -> list[HumanMessage | AIMessage]:
    return SESSION_MESSAGES.get(session_id, [])[-MAX_HISTORY_MESSAGES:]


def _append_history(session_id: str, user_input: str, answer: str):
    history = SESSION_MESSAGES.setdefault(session_id, [])
    history.extend(
        [
            HumanMessage(content=user_input),
            AIMessage(content=answer),
        ]
    )
    if len(history) > MAX_HISTORY_MESSAGES:
        SESSION_MESSAGES[session_id] = history[-MAX_HISTORY_MESSAGES:]


def clear_session(session_id: str):
    SESSION_MESSAGES.pop(session_id, None)
    SESSION_CONSTRAINTS.pop(session_id, None)


def resolve_session_id(session_id: str | None = None) -> str:
    if session_id and session_id.strip():
        return session_id.strip()
    return str(uuid4())


def _is_in_scope(user_input: str, constraints: dict[str, Any]) -> bool:
    lowered = user_input.lower()
    if any(keyword in lowered for keyword in SCOPE_KEYWORDS):
        return True
    if constraints and any(keyword in lowered for keyword in FOLLOW_UP_KEYWORDS):
        return True
    return False


def _extract_constraints(text: str) -> dict[str, Any]:
    lowered = text.lower()
    constraints: dict[str, Any] = {}

    budget_match = re.search(r"(\d+)\s*(만원|만)", text.replace(",", ""))
    if budget_match:
        constraints["budget"] = f"{budget_match.group(1)}만원"

    purposes = []
    video_editing_negated = any(
        keyword in text
        for keyword in ["영상편집은 안", "영상 편집은 안", "영상편집 안", "영상 편집 안"]
    )
    if any(keyword in lowered for keyword in ["container", "컨테이너", "docker", "도커", "리눅스", "linux"]):
        purposes.append("리눅스 container 개발")
    elif "개발" in text:
        purposes.append("개발")
    if any(keyword in lowered for keyword in ["주식", "주식거래", "트레이딩", "증권", "hts", "mts", "차트"]):
        purposes.append("주식거래")
    if any(keyword in text for keyword in ["게임", "게이밍"]):
        purposes.append("게임")
    if not video_editing_negated and any(keyword in text for keyword in ["영상편집", "영상 편집", "편집"]):
        purposes.append("영상 편집")
    if purposes:
        constraints["purpose"] = ", ".join(purposes)

    if video_editing_negated:
        constraints["exclude"] = "영상 편집"
        if constraints.get("purpose") == "영상 편집":
            constraints.pop("purpose")

    if any(keyword in text for keyword in ["휴대성은 없어", "휴대성 없어", "휴대 필요 없어", "집에서 사용", "집에서 사용할"]):
        constraints["form_factor"] = "데스크탑"
        constraints["portability"] = "불필요"
    elif any(keyword in text for keyword in ["노트북", "휴대", "들고", "이동"]):
        constraints["form_factor"] = "노트북"
        constraints["portability"] = "필요"
    elif any(keyword in text for keyword in ["미니", "소형", "맥미니", "맥 미니"]):
        constraints["form_factor"] = "소형 데스크탑"

    if any(keyword in lowered for keyword in ["ubuntu", "우분투"]):
        constraints["os"] = "Ubuntu Linux"
    elif any(keyword in lowered for keyword in ["linux", "리눅스"]):
        constraints["os"] = "Linux"

    if any(keyword in text for keyword in ["램은 충분", "ram은 충분"]):
        constraints["ram_preference"] = "기존 RAM 권장 수준이면 충분"
    elif any(keyword in lowered for keyword in ["docker", "도커", "vm", "가상"]):
        constraints["ram_preference"] = "Docker/VM 사용으로 RAM 여유 필요"

    return constraints


def _update_constraints(session_id: str, user_input: str) -> dict[str, Any]:
    current = SESSION_CONSTRAINTS.setdefault(session_id, {})
    current.update(_extract_constraints(user_input))
    purpose = str(current.get("purpose", ""))
    excluded = str(current.get("exclude", ""))
    gpu_heavy_work = any(keyword in purpose for keyword in ["게임", "영상 편집", "AI", "3D"])
    if "영상 편집" in excluded:
        gpu_heavy_work = any(keyword in purpose for keyword in ["게임", "AI", "3D"])
    if any(keyword in purpose for keyword in ["container", "개발", "주식거래"]) and not gpu_heavy_work:
        current["gpu_requirement"] = "전용 GPU 불필요"
    return current


def _constraints_to_text(constraints: dict[str, Any]) -> str:
    if not constraints:
        return "없음"
    labels = {
        "budget": "예산",
        "purpose": "사용 목적",
        "exclude": "제외 작업",
        "form_factor": "선호 형태",
        "portability": "휴대성",
        "os": "운영체제",
        "ram_preference": "RAM 조건",
        "gpu_requirement": "GPU 조건",
    }
    return "\n".join(
        f"- {labels.get(key, key)}: {value}"
        for key, value in constraints.items()
    )


def _build_contextual_query(user_input: str, constraints: dict[str, Any]) -> str:
    return f"{user_input}\n\n[누적 조건]\n{_constraints_to_text(constraints)}"


def _answer_issues(answer: str, constraints: dict[str, Any]) -> list[str]:
    issues = []
    desktop_part_names = [
        "AMD Ryzen 5 7600",
        "AMD Ryzen 7 7700",
        "AMD Ryzen 7 7800X3D",
        "AMD Ryzen 7 9700X",
        "Intel Core Ultra 5 245K",
        "Intel Core Ultra 7 265K",
    ]

    if constraints.get("form_factor") == "노트북":
        for part_name in desktop_part_names:
            if part_name in answer:
                issues.append(
                    f"노트북 추천에 데스크탑용 부품명({part_name})이 들어갔습니다."
                )

    if constraints.get("gpu_requirement") == "전용 GPU 불필요":
        if re.search(r"\bRTX\b|\bRadeon\b|\bRX\s*\d+", answer, re.IGNORECASE):
            issues.append("전용 GPU가 불필요한 조건인데 RTX/Radeon 전용 GPU가 기본 추천에 들어갔습니다.")

    if constraints.get("budget") == "100만원":
        if re.search(r"\b\d{2,3},000원", answer) or "10만원" in answer:
            issues.append("100만원 예산을 10만원 또는 100,000원 단위로 잘못 표현했습니다.")

    if constraints.get("exclude") == "영상 편집" and "영상 편집" in answer:
        issues.append("사용자가 제외한 영상 편집을 추천 이유나 질문에 다시 포함했습니다.")

    return issues


def _revise_answer_if_needed(
    messages: list,
    answer: str,
    constraints: dict[str, Any],
) -> str:
    issues = _answer_issues(answer, constraints)
    if not issues:
        return answer

    correction_prompt = HumanMessage(
        content=(
            "방금 답변에 아래 문제가 있습니다. 같은 근거를 사용하되 문제를 모두 수정해서 최종 답변만 다시 작성하세요.\n\n"
            "[수정해야 할 문제]\n"
            + "\n".join(f"- {issue}" for issue in issues)
            + "\n\n"
            "[현재까지 확정된 사용자 조건]\n"
            f"{_constraints_to_text(constraints)}\n\n"
            "특히 노트북은 완제품 탑재 사양 기준으로 설명하고, 데스크탑 부품명을 쓰지 마세요."
        )
    )
    revised = llm.invoke([*messages, AIMessage(content=answer), correction_prompt])
    return revised.content


def _normalize_answer(answer: str, constraints: dict[str, Any]) -> str:
    if constraints.get("form_factor") == "노트북":
        answer = re.sub(
            r"(?m)^(\s*[-*]\s*CPU\s*:\s*).+$",
            r"\1중급 모바일 CPU 탑재 모델",
            answer,
        )
        answer = re.sub(
            r"(?m)^(\s*[-*]\s*저장장치\s*:\s*).+$",
            r"\1NVMe SSD 512GB 이상 권장",
            answer,
        )
        answer = re.sub(
            r"(?m)^\s*[*-]\s*.*(?:Samsung|Crucial|WD Black|NVMe SSD).*?(?:개발 프로젝트|게임|편집).*?$",
            "   * NVMe SSD 512GB 이상이면 HTS 실행, 브라우저, 문서 작업에 충분한 체감 속도를 제공합니다.",
            answer,
        )
        for part_name in [
            "AMD Ryzen 5 7600",
            "AMD Ryzen 7 7700",
            "AMD Ryzen 7 7800X3D",
            "AMD Ryzen 7 9700X",
            "Intel Core Ultra 5 245K",
            "Intel Core Ultra 7 265K",
        ]:
            answer = answer.replace(part_name, "중급 모바일 CPU")

    if constraints.get("gpu_requirement") == "전용 GPU 불필요":
        answer = re.sub(
            r"(?m)^(\s*[-*]\s*GPU\s*:\s*).+$",
            r"\1내장 그래픽으로 충분",
            answer,
        )

    if constraints.get("budget") == "100만원":
        answer = re.sub(
            r"약\s*\d{2,3},000원\s*~\s*\d{2,3},000원",
            "100만원 전후",
            answer,
        )
        answer = re.sub(
            r"\d{2,3},000원\s*~\s*\d{2,3},000원",
            "100만원 전후",
            answer,
        )

    if constraints.get("exclude") == "영상 편집":
        answer = answer.replace("영상 편집", "일반 생산성 작업")

    if constraints.get("purpose") and "주식거래" in constraints["purpose"]:
        answer = answer.replace("개발 및 멀티태스킹", "HTS, 브라우저, 문서 작업 멀티태스킹")
        answer = answer.replace("개발 프로젝트와 게임/편집 병행", "HTS 실행과 여러 차트/브라우저 탭 동시 사용")

    return answer


def answer_question(user_input: str, session_id: str | None = None) -> dict[str, Any]:
    session_id = resolve_session_id(session_id)
    constraints = _update_constraints(session_id, user_input)
    if not _is_in_scope(user_input, constraints):
        _append_history(session_id, user_input, OUT_OF_SCOPE_MESSAGE)
        return {
            "session_id": session_id,
            "answer": OUT_OF_SCOPE_MESSAGE,
            "tool_calls": [],
            "sources": [],
            "history_size": len(SESSION_MESSAGES.get(session_id, [])),
            "constraints": constraints,
            "model": {
                "chat": CHAT_MODEL,
                "embedding": EMBEDDING_MODEL,
            },
        }

    contextual_query = _build_contextual_query(user_input, constraints)
    history = _get_recent_history(session_id)
    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        *history,
        HumanMessage(content=f"[현재까지 확정된 사용자 조건]\n{_constraints_to_text(constraints)}"),
        HumanMessage(content=user_input),
    ]

    response = llm_with_tools.invoke(messages)
    tool_call_records = []
    source_names = set()

    if not response.tool_calls:
        _append_history(session_id, user_input, response.content)
        return {
            "session_id": session_id,
            "answer": response.content,
            "tool_calls": [],
            "sources": [],
            "history_size": len(SESSION_MESSAGES.get(session_id, [])),
            "constraints": constraints,
            "model": {
                "chat": CHAT_MODEL,
                "embedding": EMBEDDING_MODEL,
            },
        }

    messages.append(response)
    called_tool_names = set()
    product_results = []

    for tool_call in response.tool_calls:
        tool_name = tool_call["name"]
        tool_args = tool_call["args"]
        normalized_args = normalize_tool_args(tool_args)
        called_tool_names.add(tool_name)

        selected_tool = TOOLS[tool_name]
        if tool_name == "recommend_pc_components":
            normalized_args = {"query": contextual_query}
        tool_result = selected_tool.invoke(normalized_args)
        if tool_name == "recommend_pc_components":
            product_results.append(tool_result)
            source_names.update(
                ["data/cpus.json", "data/gpus.json", "data/memory.json", "data/storage.json"]
            )
        else:
            source_names.update(_extract_sources(tool_result))

        tool_call_records.append(
            {
                "name": tool_name,
                "args": normalized_args,
                "result_preview": tool_result[:500],
                "supplemental": False,
            }
        )
        messages.append(
            ToolMessage(
                content=tool_result,
                tool_call_id=tool_call["id"],
            )
        )

    supplemental_results = []
    for tool_name in (
        "search_computer_buying_guide",
        "search_computer_specs",
        "recommend_pc_components",
    ):
        if tool_name in called_tool_names:
            continue

        tool_query = contextual_query if tool_name == "recommend_pc_components" else user_input
        tool_result = TOOLS[tool_name].invoke({"query": tool_query})
        if tool_name == "recommend_pc_components":
            product_results.append(tool_result)
            source_names.update(
                ["data/cpus.json", "data/gpus.json", "data/memory.json", "data/storage.json"]
            )
        else:
            source_names.update(_extract_sources(tool_result))

        tool_call_records.append(
            {
                "name": tool_name,
                "args": {"query": tool_query},
                "result_preview": tool_result[:500],
                "supplemental": True,
            }
        )
        supplemental_results.append(f"[{tool_name}]\n{tool_result}")

    if supplemental_results:
        messages.append(
            HumanMessage(
                content="[보강 검색 결과]\n\n" + "\n\n".join(supplemental_results)
            )
        )

    messages.append(
        HumanMessage(
            content=f"[현재까지 확정된 사용자 조건]\n{_constraints_to_text(constraints)}"
        )
    )
    messages.append(_final_instruction(user_input, product_results, constraints))
    final = llm.invoke(messages)
    answer = _revise_answer_if_needed(messages, final.content, constraints)
    answer = _normalize_answer(answer, constraints)
    _append_history(session_id, user_input, answer)

    return {
        "session_id": session_id,
        "answer": answer,
        "tool_calls": tool_call_records,
        "sources": sorted(source_names),
        "history_size": len(SESSION_MESSAGES.get(session_id, [])),
        "constraints": constraints,
        "model": {
            "chat": CHAT_MODEL,
            "embedding": EMBEDDING_MODEL,
        },
    }
