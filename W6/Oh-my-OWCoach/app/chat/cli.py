"""OW-MY-COACH CLI — multiturn Overwatch coaching chat.

Usage:
    python -m app.chat.cli                      # interactive REPL
    python -m app.chat.cli --role dps --map "King's Row"
    python -m app.chat.cli --ask "겐지 상대가 너무 힘든데 어떻게 해?"   # one-shot

Commands inside the REPL:
    /role <탱커|딜러|힐러>   현재 역할군 설정
    /map  <맵 이름>          현재 맵 설정
    /reset                   대화 기록 초기화
    /quit                    종료
"""
from __future__ import annotations

import argparse

from app.chat.coach import Coach
from app.chat.query_router import role_label

_ROLE_ALIASES = {
    "탱커": "tank", "탱": "tank", "돌격": "tank", "tank": "tank",
    "딜러": "dps", "딜": "dps", "공격": "dps", "dps": "dps",
    "힐러": "support", "힐": "support", "지원": "support", "서폿": "support", "support": "support",
}


def _norm_role(value: str | None) -> str | None:
    if not value:
        return None
    return _ROLE_ALIASES.get(value.strip().lower(), value.strip().lower())


def _print_answer(result: dict) -> None:
    route = result["route"]
    print(f"\n🎯 [의도: {route['intent']} | 역할군: {role_label(route.get('role'))} | 맵: {route.get('map') or '미지정'}]")
    print(f"\n{result['answer']}\n")
    if result["sources"]:
        print("📚 출처:")
        for s in result["sources"]:
            print(f"  - {s}")
    print("-" * 60)


def run_repl(coach: Coach, role: str | None, game_map: str | None) -> int:
    history: list[dict] = []
    print("=" * 60)
    print(" OW-MY-COACH — 오버워치 경쟁전 코치 챗봇")
    print(" 역할군/맵을 알려주면 더 정확히 코칭합니다.")
    print(" 명령어: /role <탱커|딜러|힐러>  /map <맵>  /reset  /quit")
    print("=" * 60)
    print(f"현재 설정 → 역할군: {role_label(role)} | 맵: {game_map or '미지정'}\n")

    while True:
        try:
            user = input("나 > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n코치를 종료합니다. GG!")
            return 0
        if not user:
            continue
        if user in ("/quit", "/exit", "/q"):
            print("코치를 종료합니다. GG!")
            return 0
        if user == "/reset":
            history = []
            print("[대화 기록을 초기화했습니다.]")
            continue
        if user.startswith("/role"):
            role = _norm_role(user[len("/role"):])
            print(f"[역할군 설정 → {role_label(role)}]")
            continue
        if user.startswith("/map"):
            game_map = user[len("/map"):].strip() or None
            print(f"[맵 설정 → {game_map or '미지정'}]")
            continue

        print("코치 > (생각 중...)")
        result = coach.ask(user, history=history, user_role=role, user_map=game_map)
        _print_answer(result)
        history.append({"role": "user", "content": user})
        history.append({"role": "assistant", "content": result["answer"]})
        # Keep history bounded (last 6 turns) to control prompt size.
        history[:] = history[-12:]


def main() -> int:
    parser = argparse.ArgumentParser(description="OW-MY-COACH Overwatch coaching chatbot.")
    parser.add_argument("--role", default=None, help="기본 역할군 (탱커/딜러/힐러)")
    parser.add_argument("--map", dest="game_map", default=None, help="기본 맵 이름")
    parser.add_argument("--ask", default=None, help="단발성 질문(비대화형)")
    args = parser.parse_args()

    coach = Coach()
    role = _norm_role(args.role)

    if args.ask:
        result = coach.ask(args.ask, user_role=role, user_map=args.game_map)
        _print_answer(result)
        return 0

    return run_repl(coach, role, args.game_map)


if __name__ == "__main__":
    raise SystemExit(main())
