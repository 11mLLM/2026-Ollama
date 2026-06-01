# app.py
# Local Life Assistant 메인 실행 파일

from langchain_ollama import ChatOllama
from langchain_core.messages import HumanMessage, SystemMessage

from rag import search_personal_docs
from prompts import (
    get_rag_answer_prompt,
    get_email_rewrite_prompt,
    get_checklist_prompt,
)


MODEL_NAME = "exaone3.5:7.8b"


def load_llm():
    """Ollama 로컬 LLM을 불러온다."""
    return ChatOllama(
        model=MODEL_NAME,
        temperature=0.2
    )


def ask_personal_document(llm):
    """개인 문서 기반 Q&A 기능"""
    question = input("\n질문을 입력하세요: ").strip()

    if not question:
        print("질문이 비어 있습니다.")
        return

    print("\n개인 문서에서 관련 내용을 검색 중입니다...")

    context = search_personal_docs(question, k=3)

    prompt = get_rag_answer_prompt(
        context=context,
        question=question
    )

    response = llm.invoke([
        SystemMessage(content="당신은 개인 문서 기반 로컬 AI 어시스턴트입니다."),
        HumanMessage(content=prompt)
    ])

    print("\n[답변]")
    print(response.content)


def rewrite_email(llm):
    """메일/메시지 다듬기 기능"""
    print("\n다듬고 싶은 메일 또는 메시지 초안을 입력하세요.")
    print("입력이 끝나면 Enter를 두 번 누르세요.\n")

    lines = []
    while True:
        line = input()
        if line == "":
            break
        lines.append(line)

    user_text = "\n".join(lines).strip()

    if not user_text:
        print("입력 내용이 비어 있습니다.")
        return

    prompt = get_email_rewrite_prompt(user_text)

    response = llm.invoke([
        SystemMessage(content="당신은 한국어 메일 작성 도우미입니다."),
        HumanMessage(content=prompt)
    ])

    print("\n[다듬은 메일]")
    print(response.content)


def make_checklist(llm):
    """체크리스트 생성 기능"""
    situation = input("\n체크리스트를 만들 상황을 입력하세요: ").strip()

    if not situation:
        print("입력 내용이 비어 있습니다.")
        return

    prompt = get_checklist_prompt(situation)

    response = llm.invoke([
        SystemMessage(content="당신은 실용적인 체크리스트 작성 도우미입니다."),
        HumanMessage(content=prompt)
    ])

    print("\n[체크리스트]")
    print(response.content)


def print_menu():
    """메뉴 출력"""
    print("\n" + "=" * 50)
    print("Local Life Assistant")
    print("=" * 50)
    print("1. 개인 문서에 질문하기")
    print("2. 메일/메시지 다듬기")
    print("3. 체크리스트 만들기")
    print("4. 종료")
    print("=" * 50)


def main():
    print("Ollama 기반 로컬 개인 생활 어시스턴트를 시작합니다.")
    print("모델 로딩 중입니다...")

    llm = load_llm()

    while True:
        print_menu()
        choice = input("메뉴 선택: ").strip()

        if choice == "1":
            ask_personal_document(llm)

        elif choice == "2":
            rewrite_email(llm)

        elif choice == "3":
            make_checklist(llm)

        elif choice == "4":
            print("프로그램을 종료합니다.")
            break

        else:
            print("올바른 메뉴 번호를 입력하세요.")


if __name__ == "__main__":
    main()