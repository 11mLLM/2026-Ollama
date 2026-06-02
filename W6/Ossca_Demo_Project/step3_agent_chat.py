from agent.advisor import answer_question


CLI_SESSION_ID = "cli"


def run_agent(user_input: str):
    print(f"\n{'=' * 60}")
    print(f"[사용자] {user_input}")

    result = answer_question(user_input, CLI_SESSION_ID)
    for tool_call in result["tool_calls"]:
        marker = "보강 호출" if tool_call["supplemental"] else "호출"
        print(f"[Tool] {tool_call['name']} {marker}: {tool_call['args']}")

    if result["sources"]:
        print(f"[Sources] {', '.join(result['sources'])}")
    print(f"[Session] {result['session_id']} / history_size={result['history_size']}")
    print(f"[답변]\n{result['answer']}")


if __name__ == "__main__":
    print("computer_advisor_rag 챗봇 시작 (종료: exit 또는 quit)")
    print("-" * 60)

    while True:
        user_input = input("\n질문: ").strip()
        if not user_input:
            continue
        if user_input.lower() in ("exit", "quit"):
            print("종료합니다.")
            break
        run_agent(user_input)
