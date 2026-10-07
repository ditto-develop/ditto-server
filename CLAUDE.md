@AGENTS.md

## Claude Code

- 편집에 들어가기 전, 광범위한 변경은 계획을 먼저 요약한다.
- DB 마이그레이션·보안(`SecurityConfig`/API Key)·공개 API 변경은 착수 전 위험을 설명하고 멈춘다.
- 경로별 세부 규칙은 이 파일을 키우지 말고 `.claude/rules/`에 path-scoped로 추가한다.
- 큰 문서를 여기 import 하지 말 것. `docs/`는 작업에 필요할 때만 읽는다.

## 리뷰 자동화

- 멀티에이전트 리뷰 workflow는 명시 요청 시만. 단 모든 Task 완료 시 리뷰 5종(코드 품질 kotlin-ddd-reviewer · 버그 bug-reviewer · 디자인 · 가독성 · QA 편의성)을 백그라운드로 함께 1회 실행 후 결과 보고. 화면 변경이 없으면 디자인은 생략
- 순서: 마지막 Task 커밋 → 리뷰 5종 → 지적 반영(커밋 승인) → push + PR. 리뷰 전에 push·PR 하지 않는다
