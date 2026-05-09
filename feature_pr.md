# PR 템플릿: 기능 추가

### Description
- 이번 PR에서 어떤 기능을 추가했는지 간단히 작성하세요.

### Motivation
- 왜 이 기능이 필요한지 작성하세요.
- 어떤 문제를 해결하거나 어떤 사용성을 개선하는지 설명하세요.

### Summary of Changes
- 새로 추가된 컴포넌트/함수/모듈 등 주요 변경을 나열하세요.
- 예: `src/js/environments.ts`에 `RuntimeOption` 추가
- 예: `src/js/pyodide.ts`에 `runtime` 옵션 추가

### Noteworthy Choices
- 주요 설계/구현 선택 사항을 기술하세요.
- 예: 초기화 시 오버라이드만 허용하고 런타임 에뮬레이션은 하지 않음

### Example Usage
- 사용 예시를 작성하세요.
- 예: `const pyodide = await loadPyodide({ runtime: "node" });`

### Checklist
- [ ] 기능 추가 / 구현 완료
- [ ] 테스트 케이스 작성 또는 기존 테스트 통과
- [ ] 문서 또는 주석 업데이트
- [ ] 관련 이슈 번호 기재
