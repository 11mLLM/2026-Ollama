# PR 템플릿: 버그 수정

### Description
- 발생한 버그를 간략히 작성하세요.

### Motivation
- 왜 이 버그를 해결해야 하는지 작성하세요.
- 어떤 환경/시나리오에서 문제가 발생하는지 설명하세요.

### Summary of Changes
- 어떤 코드/로직을 수정했는지 나열하세요.
- 예: `src/js/scheduler.ts`에 `addEventListener` 체크 추가

### Noteworthy Choices
- 방어적 체크를 추가한 이유, 기존 동작과의 차이를 작성하세요.
- 예: Node.js에서 브라우저 API가 없을 때도 안전하게 동작하도록 처리

### Example Usage
- 재현 방법 또는 수정 후 결과를 간단히 작성하세요.
- 예: `loadPyodide({ runtime: "browser" })` 실행 시 `addEventListener` 오류 없음

### Checklist
- [ ] 버그 재현 경로 확인
- [ ] 수정 후 동작 검증 완료
- [ ] 테스트 및 린트 통과
- [ ] 관련 이슈 번호 기재
