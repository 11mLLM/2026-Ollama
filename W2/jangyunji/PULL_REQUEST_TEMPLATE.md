# Pull Request

### Description
- PR에서 어떤 변경을 했는지 간단히 서술하세요.

### Motivation
- 이 변경이 왜 필요한지 작성하세요.
- 어떤 문제를 해결하거나 어떤 사용성을 개선하는지 설명하세요.

### Summary of Changes
- 주요 변경 사항을 항목별로 작성하세요.
- 예: `src/js/environments.ts`에 `RuntimeOption` 추가
- 예: `src/js/emscripten-settings.ts`에 오버라이드 적용

### Noteworthy Choices
- 설계/구현 선택 사항을 작성하세요.
- 예: 초기화 시 런타임 오버라이드만 처리하고 API 에뮬레이션은 하지 않음

### Example Usage
- 코드 사용 예시를 작성하세요.
- 예: `const pyodide = await loadPyodide({ runtime: "node" });`

### Checklist
- [ ] CHANGELOG 항목 추가 여부 검토
- [ ] 테스트 추가 또는 기존 테스트 업데이트
- [ ] 문서 업데이트 여부 확인
- [ ] 관련 이슈 번호 기재
