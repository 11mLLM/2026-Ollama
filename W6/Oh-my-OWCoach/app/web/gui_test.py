"""Playwright GUI test for OW-MY-COACH.

Drives the web chat end-to-end against a running server:
    1) start the server:  uvicorn app.web.server:app --port 8000
    2) run this test:      python -m app.web.gui_test

Verifies: page loads, role/map controls work, a coaching question produces a
grounded answer bubble with intent meta and sources. EXAONE is slow, so the
answer wait uses a long timeout.
"""
from __future__ import annotations

import sys

from playwright.sync_api import sync_playwright

BASE_URL = "http://127.0.0.1:8000"
ANSWER_TIMEOUT_MS = 240_000  # EXAONE 7.8B Q4 can take a while


def run() -> int:
    failures: list[str] = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.set_default_timeout(15_000)

        # 1) Load page
        page.goto(BASE_URL, wait_until="domcontentloaded")
        assert "OW-MY-COACH" in page.title(), "title missing"
        print("[ok] page loaded, title:", page.title())

        # 2) Set role + map via controls
        page.select_option("#role-select", "support")
        page.fill("#map-input", "왕의 길")
        print("[ok] role=support, map=왕의 길 set")

        # 3) Type a coaching question and send
        question = "상대 트레이서 겐지가 백라인 다이브 자꾸 오는데 어떻게 대응해?"
        page.fill("#message-input", question)
        page.click("#send-btn")
        print("[ok] question sent:", question)

        # user bubble appears
        page.wait_for_selector('.msg.user', timeout=10_000)
        # thinking indicator appears then disappears
        page.wait_for_selector('[data-testid="thinking"]', timeout=10_000)
        print("[ok] user bubble + thinking indicator shown")

        # 4) Wait for the coach answer (long — model generation)
        print("[..] waiting for EXAONE answer (up to %ds)..." % (ANSWER_TIMEOUT_MS // 1000))
        page.wait_for_selector('[data-testid="coach-answer"]', timeout=ANSWER_TIMEOUT_MS)
        answer_el = page.query_selector('[data-testid="coach-answer"]')
        answer_text = answer_el.inner_text() if answer_el else ""
        print("\n----- ANSWER (first 600 chars) -----")
        print(answer_text[:600])
        print("------------------------------------\n")

        # 5) Assertions
        if len(answer_text) < 40:
            failures.append("answer too short")
        if "의도:" not in answer_text:
            failures.append("intent meta missing")
        sources_el = answer_el.query_selector(".sources") if answer_el else None
        if not sources_el:
            failures.append("sources block missing")
        else:
            print("[ok] sources:", sources_el.inner_text()[:120])

        # thinking indicator should be gone
        if page.query_selector('[data-testid="thinking"]'):
            failures.append("thinking indicator did not clear")

        page.screenshot(path="output/ow_my_coach_gui.png", full_page=True)
        print("[ok] screenshot saved -> output/ow_my_coach_gui.png")
        browser.close()

    if failures:
        print("\nFAIL:", "; ".join(failures))
        return 1
    print("\nPASS: web GUI end-to-end works (grounded answer + meta + sources).")
    return 0


if __name__ == "__main__":
    sys.exit(run())
