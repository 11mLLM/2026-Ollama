# PR 작성 계획

## Context

현재 브랜치 `task/w2/kang-ji-hoon`는 `main` 대비 1개의 커밋(`158d4be docs: .md 파일들 생성`)만 가지고 있으며, 변경 사항은 `.github/` 디렉터리 하위의 협업용 마크다운 템플릿 추가입니다.

- `.github/pull_request_template.md`
- `.github/ISSUE_TEMPLATE/bug_report.md`
- `.github/ISSUE_TEMPLATE/feature_request.md`
- `.github/ISSUE_TEMPLATE/task.md`

코드 변경(UI/로직)은 없고 문서/템플릿만 추가되는 PR이므로, 레포의 PR 템플릿(`.github/pull_request_template.md`)에 맞추어 본문을 작성하되 해당하지 않는 섹션(코드 설명, 구현화면)은 템플릿 가이드(`<!-- 없으면 생략 -->`)에 따라 정리합니다. 사용자 확인 결과 연결된 이슈는 없으므로 `🔗 연결된 이슈` 섹션도 제거합니다.

## PR 메타데이터

- **Base**: `main`
- **Head**: `task/w2/kang-ji-hoon`
- **Title**: `docs: GitHub 이슈/PR 템플릿 추가`
- **Repo**: `11mLLM/2026-Ollama`

## PR 본문 (실제 등록할 내용)

```markdown
## 📸 작업 내용

> 협업 시 일관된 PR/이슈 작성 흐름을 위해 GitHub 템플릿 4종을 추가했습니다.

- `.github/pull_request_template.md` — PR 작성 템플릿 추가
- `.github/ISSUE_TEMPLATE/bug_report.md` — 버그 리포트 이슈 템플릿 추가
- `.github/ISSUE_TEMPLATE/feature_request.md` — 기능 요청 이슈 템플릿 추가
- `.github/ISSUE_TEMPLATE/task.md` — 일반 작업 이슈 템플릿 추가
```

> 비고: 이번 PR은 문서/템플릿 추가만 포함하므로 PR 템플릿의 `🎞️ 주요 코드 설명`, `🖥️ 구현화면`, `🔗 연결된 이슈`, `📚 참고자료`, `💬 기타 더 이야기해볼 점` 섹션은 템플릿 주석의 안내(`없으면 제목까지 완전히 지워주세요`)에 따라 제거합니다.

## 실행 방법

1. 변경 사항이 이미 origin에 푸시되어 있는지 확인 (`git status`상 up to date 확인 완료)
2. `gh pr create` 명령으로 PR 생성:
   ```bash
   gh pr create \
     --base main \
     --head task/w2/kang-ji-hoon \
     --title "docs: GitHub 이슈/PR 템플릿 추가" \
     --body "$(cat <<'EOF'
   ## 📸 작업 내용

   > 협업 시 일관된 PR/이슈 작성 흐름을 위해 GitHub 템플릿 4종을 추가했습니다.

   - `.github/pull_request_template.md` — PR 작성 템플릿 추가
   - `.github/ISSUE_TEMPLATE/bug_report.md` — 버그 리포트 이슈 템플릿 추가
   - `.github/ISSUE_TEMPLATE/feature_request.md` — 기능 요청 이슈 템플릿 추가
   - `.github/ISSUE_TEMPLATE/task.md` — 일반 작업 이슈 템플릿 추가
   EOF
   )"
   ```

## 확인(Verification)

- PR 생성 후 반환되는 URL을 사용자에게 제공
- `gh pr view --web` 또는 URL 접속으로 본문이 템플릿 형식에 맞게 렌더링되는지 확인
- Base/Head 브랜치가 `main` ← `task/w2/kang-ji-hoon`로 설정되었는지 확인

## 참고 파일

- `/Users/2sac/Documents/github/2026-Ollama/.github/pull_request_template.md`
- `/Users/2sac/Documents/github/2026-Ollama/.github/ISSUE_TEMPLATE/*.md`
