import json
import re
from pathlib import Path

from langchain_core.tools import tool


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"

PURPOSE_ALIASES = {
    "development": ["개발", "코딩", "프로그래밍", "docker", "도커", "ide", "백엔드", "프론트"],
    "gaming": ["게임", "게이밍", "스팀", "qhd", "fhd", "고주사율"],
    "video_editing": ["영상", "편집", "프리미어", "다빈치", "유튜브", "4k"],
    "ai": ["ai", "머신러닝", "딥러닝", "llm", "cuda"],
    "3d": ["3d", "블렌더", "렌더링"],
    "stock_trading": ["주식", "주식거래", "트레이딩", "증권", "hts", "mts", "차트"],
    "general": ["문서", "웹서핑", "사무", "온라인 강의"],
}


def _load_catalog(filename: str) -> list[dict]:
    with (DATA_DIR / filename).open(encoding="utf-8") as file:
        return json.load(file)


def _detect_budget(query: str) -> int | None:
    normalized = query.replace(",", "").replace(" ", "")
    match = re.search(r"(\d+)(만원|만)", normalized)
    if match:
        return int(match.group(1)) * 10000
    match = re.search(r"(\d{6,})", normalized)
    if match:
        return int(match.group(1))
    return None


def _budget_level(budget_krw: int | None) -> str:
    if budget_krw is None:
        return "mid"
    if budget_krw < 1_000_000:
        return "low"
    if budget_krw < 1_800_000:
        return "mid"
    if budget_krw < 2_500_000:
        return "high"
    return "premium"


def _detect_purposes(query: str) -> list[str]:
    lowered = query.lower()
    purposes = []
    for purpose, aliases in PURPOSE_ALIASES.items():
        if purpose == "video_editing" and any(
            keyword in lowered
            for keyword in ["영상편집은 안", "영상 편집은 안", "영상편집 안", "영상 편집 안"]
        ):
            continue
        if any(alias in lowered for alias in aliases):
            purposes.append(purpose)
    return purposes or ["general"]


def _gpu_not_required(query: str, purposes: list[str]) -> bool:
    lowered = query.lower()
    explicit_gpu = any(keyword in lowered for keyword in ["gpu", "그래픽", "cuda", "게임", "ai", "3d"])
    return (
        ("development" in purposes or "stock_trading" in purposes or "general" in purposes)
        and not explicit_gpu
    )


def _detect_form_factor(query: str) -> str:
    if any(keyword in query for keyword in ["휴대성은 없어", "휴대성 없어", "휴대 필요 없어", "휴대성은 없어도"]):
        return "desktop"
    if any(keyword in query for keyword in ["노트북", "휴대", "들고", "이동", "카페", "학교"]):
        return "laptop"
    if any(keyword in query for keyword in ["미니", "맥미니", "맥 미니", "소형"]):
        return "mini_desktop"
    if any(keyword in query for keyword in ["데스크탑", "데스크톱", "조립", "본체"]):
        return "desktop"
    return "unknown"


def _score_item(item: dict, purposes: list[str], budget_level: str, form_factor: str) -> int:
    score = 0
    item_use_cases = set(item.get("use_cases", []))

    for purpose in purposes:
        if purpose in item_use_cases:
            score += 4
        if purpose == "development" and "heavy_development" in item_use_cases:
            score += 2
        if purpose == "video_editing" and item.get("capacity_gb", 0) >= 32:
            score += 1
        if purpose == "ai" and item.get("vram_gb", 0) >= 12:
            score += 2

    levels = ["low", "mid", "high", "premium"]
    item_level = item.get("budget_level", "mid")
    if item_level == budget_level:
        score += 3
    elif abs(levels.index(item_level) - levels.index(budget_level)) == 1:
        score += 1
    elif levels.index(item_level) > levels.index(budget_level):
        score -= 2

    if form_factor == "laptop" and item.get("category") in {"cpu", "gpu"}:
        score -= 1
    if form_factor == "laptop" and item.get("type") == "LPDDR5X":
        score += 4

    return score


def _top_items(items: list[dict], purposes: list[str], budget_level: str, form_factor: str, limit: int = 2):
    scored = [
        (_score_item(item, purposes, budget_level, form_factor), item)
        for item in items
    ]
    scored.sort(key=lambda pair: pair[0], reverse=True)
    return [item for score, item in scored[:limit] if score > 0]


def _format_item(item: dict) -> str:
    strengths = ", ".join(item.get("strengths", []))
    cautions = " ".join(item.get("cautions", []))
    return f"- {item['name']}: {strengths}. 주의: {cautions}"


@tool
def recommend_pc_components(query: str) -> str:
    """사용자 목적, 예산, 폼팩터에 맞는 CPU, GPU, RAM, 저장장치 실 제품 후보를 추천합니다."""
    budget_krw = _detect_budget(query)
    budget_level = _budget_level(budget_krw)
    purposes = _detect_purposes(query)
    form_factor = _detect_form_factor(query)

    cpus = _top_items(_load_catalog("cpus.json"), purposes, budget_level, form_factor)
    gpus = [] if _gpu_not_required(query, purposes) else _top_items(
        _load_catalog("gpus.json"),
        purposes,
        budget_level,
        form_factor,
    )
    memory = _top_items(_load_catalog("memory.json"), purposes, budget_level, form_factor)
    storage = _top_items(_load_catalog("storage.json"), purposes, budget_level, form_factor)

    budget_text = f"{budget_krw:,}원" if budget_krw else "미확인"
    lines = [
        "[제품 카탈로그 추천]",
        f"- 감지한 목적: {', '.join(purposes)}",
        f"- 감지한 예산: {budget_text} ({budget_level})",
        f"- 감지한 형태: {form_factor}",
        "",
        "CPU 후보:",
        *(_format_item(item) for item in cpus),
        "",
        "GPU 후보:",
        *(_format_item(item) for item in gpus),
        *([] if gpus else ["- 전용 GPU 불필요: 리눅스 컨테이너 개발 중심이면 내장 그래픽 또는 기본 출력용 그래픽으로 충분합니다."]),
        "",
        "RAM 후보:",
        *(_format_item(item) for item in memory),
        "",
        "저장장치 후보:",
        *(_format_item(item) for item in storage),
        "",
        "호환성 메모:",
        "- 데스크탑 CPU는 메인보드 소켓과 RAM 규격을 함께 맞춰야 합니다.",
        "- 노트북은 CPU/GPU를 개별 구매하는 방식이 아니라 해당 사양을 탑재한 완제품을 고르는 방식입니다.",
        "- GPU가 필요한 작업은 파워 용량, 케이스 크기, 보조전원 커넥터도 확인해야 합니다.",
    ]
    return "\n".join(lines)
