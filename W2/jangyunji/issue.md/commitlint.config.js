module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // 커밋 타입은 다음 항목 중 하나여야 합니다.
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'chore', 'build', 'ci', 'revert']
    ],
    // scope 형식 검사 비활성화
    'scope-case': [0, 'always', []],
    // 제목 형식 검사 비활성화
    'subject-case': [0, 'never', []],
    // 커밋 메시지 헤더 최대 길이 제한
    'header-max-length': [2, 'always', 100]
  }
};
