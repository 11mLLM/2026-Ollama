import os
import sys

from capture import capture_screen_base64
from perceive import perceive

SAVE_PATH = "/tmp/screen_goblin_check.png"


def main():
    import base64

    encoded = capture_screen_base64()
    with open(SAVE_PATH, "wb") as handle:
        handle.write(base64.b64decode(encoded))
    print(f"[1/2] 스크린샷 저장: {SAVE_PATH}")
    print("      이 파일을 열어보세요. 지금 보고 있는 '앱 창'이 보이면 권한 정상,")
    print("      바탕화면만 보이면 화면 기록 권한이 없는 것입니다.")
    print()
    print("[2/2] moondream 인식 결과:")
    print("     ", perceive(encoded))
    print()
    print("결과가 실제 화면과 다르면 → 시스템 설정 > 개인정보 보호 및 보안 > 화면 기록 에서")
    print("터미널 앱을 켜고 터미널을 완전히 종료 후 다시 실행하세요.")


if __name__ == "__main__":
    sys.exit(main())
