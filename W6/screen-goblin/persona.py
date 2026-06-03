import difflib
import json
import re
from collections import deque

import requests

from config import (
    CHARACTER_NAME,
    DEFAULT_MOOD,
    MEMORY_SIZE,
    MOOD_FACES,
    OLLAMA_URL,
    PERSONA_MODEL,
    STUCK_REPEAT_COUNT,
    STUCK_SIMILARITY_THRESHOLD,
)
from perceive import UNCLEAR_SCENE

SYSTEM_PROMPT = f"""너는 사용자 모니터 구석에 사는 시니컬한 팩폭 고양이 '{CHARACTER_NAME}'이다.
화면에서 사용자가 뭘 하는지에 대한 영어 묘사를 받으면, 짧고 시니컬한 한국어 드립을 친다.

언어 규칙(매우 중요):
- comment는 무조건 한국어로만 쓴다. 영어 단어/중국어/한자/일본어를 절대 섞지 않는다.
- 고유명사(YouTube, VS Code 등)는 그대로 둬도 되지만 문장은 한국어다.

말투 규칙:
- 욕설과 인신공격은 금지. 위트있는 팩폭만 한다.
- 한두 문장으로 짧게. 이모지는 최대 1개.
- "또?", "아까부터" 같은 질린 표현은 'repeated'가 true일 때만 쓴다. 평소에는 쓰지 않는다.
- help_mode가 true이면 드립 대신 진짜 짧고 구체적인 도움 팁 한 줄을 준다.
- 화면 묘사가 모호하거나 알아볼 수 없으면(unclear) 억지로 지어내지 말고 "또 뭘 보는 거야, 화면이 흐릿한데" 같이 솔직하게 친다.

mood는 장면 성격에 맞게 매번 다르게 고른다:
- 평온: 평범하게 작업/감상 중일 때
- 어이없음: 뜬금없거나 노는 화면(영상, 게임, 밈)일 때
- 질림: repeated가 true이거나 똑같은 짓 반복일 때
- 감탄: 멋진 결과물/사진/완성된 화면일 때
- 걱정: 에러, 오류, 막힘, 또는 화면이 unclear일 때

반드시 JSON으로만 답한다. 형식: {{"comment": "한국어 한두 문장", "mood": "평온|어이없음|질림|감탄|걱정"}}"""

HISTORY_MAXLEN = max(MEMORY_SIZE, STUCK_REPEAT_COUNT)

FOREIGN_PATTERN = re.compile(r"[　-〿぀-ヿ一-鿿＀-￯]")
RETRY_LIMIT = 2


def _has_foreign(text):
    return bool(FOREIGN_PATTERN.search(text))


def _strip_foreign(text):
    cleaned = FOREIGN_PATTERN.sub("", text)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" ,.!?")
    return cleaned.strip() or "..."


class PersonaEngine:
    def __init__(self):
        self.history = deque(maxlen=HISTORY_MAXLEN)

    def comment(self, scene):
        unclear = scene == UNCLEAR_SCENE
        help_mode = self._is_stuck(scene)
        repeated = unclear is False and self._is_repeated(scene)
        recent = "\n".join(f"- {item}" for item in list(self.history)[-MEMORY_SIZE:]) or "(없음)"
        user_prompt = (
            f"최근 관찰 기록:\n{recent}\n\n"
            f"현재 화면 묘사: {scene}\n"
            f"unclear: {str(unclear).lower()}\n"
            f"repeated: {str(repeated).lower()}\n"
            f"help_mode: {str(help_mode).lower()}"
        )
        try:
            result = self._generate(user_prompt)
        except requests.RequestException as error:
            result = {"comment": f"(입을 다물었다: {error})", "mood": DEFAULT_MOOD}
        if unclear:
            result["mood"] = "걱정"
        else:
            self.history.append(scene)
        result["help_mode"] = help_mode
        return result

    def _generate(self, user_prompt):
        result = None
        for _ in range(RETRY_LIMIT + 1):
            response = requests.post(
                f"{OLLAMA_URL}/api/generate",
                json={
                    "model": PERSONA_MODEL,
                    "system": SYSTEM_PROMPT,
                    "prompt": user_prompt,
                    "format": "json",
                    "stream": False,
                    "options": {"temperature": 0.6, "top_p": 0.85},
                },
                timeout=120,
            )
            response.raise_for_status()
            result = self._parse(response.json()["response"])
            if not _has_foreign(result["comment"]):
                return result
        result["comment"] = _strip_foreign(result["comment"])
        return result

    def _is_repeated(self, scene):
        if not self.history:
            return False
        last = self.history[-1]
        return difflib.SequenceMatcher(None, last, scene).ratio() >= STUCK_SIMILARITY_THRESHOLD

    def _is_stuck(self, scene):
        needed = STUCK_REPEAT_COUNT - 1
        if len(self.history) < needed:
            return False
        for past in list(self.history)[-needed:]:
            if difflib.SequenceMatcher(None, past, scene).ratio() < STUCK_SIMILARITY_THRESHOLD:
                return False
        return True

    def _parse(self, raw):
        try:
            data = json.loads(raw)
            comment = str(data.get("comment", "")).strip() or "..."
            mood = data.get("mood", DEFAULT_MOOD)
            if mood not in MOOD_FACES:
                mood = DEFAULT_MOOD
            return {"comment": comment, "mood": mood}
        except (json.JSONDecodeError, TypeError):
            return {"comment": raw.strip()[:200] or "...", "mood": DEFAULT_MOOD}


if __name__ == "__main__":
    engine = PersonaEngine()
    scenes = [
        "The user is watching a YouTube video in a web browser.",
        "The user is watching a YouTube video in a web browser.",
        "The user is staring at a Python error traceback in VS Code.",
        "The user is staring at a Python error traceback in VS Code.",
        "The user is staring at a Python error traceback in VS Code.",
    ]
    for scene in scenes:
        print(scene)
        print("   ->", engine.comment(scene))
