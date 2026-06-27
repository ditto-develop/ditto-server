# ADR 0001 — AI 에이전트 문서 계층 도입

- 상태: Accepted (2026-06-27)
- 관련: 이슈 #91

## Context

컨벤션이 루트 `PROJECT_CONVENTION.md`(349줄) 한 문서에 모여 있었고, "새 세션마다 먼저 읽어라"는 지시로 매 세션 통째로 컨텍스트에 올랐다. 이는 "항상 읽는 문서는 얇게" 원칙에 어긋나(노이즈·비용·준수율 저하), 또 컨벤션이 한 곳에만 있어 "이 파일을 만질 때 이 규칙" 같은 경로별 적용이 약했다.

## Decision

문서를 **로딩 경계(언제 읽히는가)** 기준으로 계층화한다.

- `AGENTS.md` — 도구중립 SSOT, 항상 로드. 전역 불변식·모듈 지도·빌드명령·머지게이트만(얇게).
- `CLAUDE.md` — `@AGENTS.md` 임포트 + Claude 전용 행동 몇 줄.
- `.claude/rules/*.md` — 경로 매칭 시 자동 로드되는 **순수 포인터**(규칙을 인라인하지 않고 해당 `docs/`로 안내).
- `docs/` — 온디맨드. `modules/`(모듈별)·`testing/`·`domains/`·`adr/`.
- `PROJECT_CONVENTION.md`는 위로 전부 이전 후 **삭제(해체)**.

핵심 선택과 근거:
- **rules는 순수 포인터, 규칙 SSOT는 docs.** 인라인은 "확정 로드"일 뿐 "확정 준수"가 아니다. 진짜 강제는 검사기(lint/test/CI) 몫이고, 중요한 must-do는 대부분 검사 가능하므로 인라인의 이점이 작다 → 중복을 피한다.
- **모듈 문서는 `docs/modules/`에**(모듈 디렉터리는 코드만). 디렉터리에 README/CLAUDE.md를 흩지 않는다.
- **`kotlin-ddd-reviewer`를 repo `.claude/agents/`로 승격**해 팀 공유(`.gitignore`를 `.claude/*` + `!rules` + `!agents`로 선택적 un-ignore).
- **브랜치 규칙은 `feature/<이슈번호>`**로 통일(전역 개인 기본값보다 repo 규칙 우선).

## Consequences

- 얻음: 항상-로드 컨텍스트 축소, 작업 파일에 맞는 규칙 자동 안내, 규칙 SSOT, 규칙·리뷰어 팀 공유.
- 비용: 문서가 여러 파일로 분산(탐색은 rules 포인터로 해결). 온디맨드 docs는 "확정 읽힘"을 보장하지 않음 → stub은 강한 포인터(명령형+트리거+티저)로 확률을 높이고, 각 docs는 맨 위 "필수" 블록을 둔다.
- 후속: 검사 가능·치명적 규칙은 검사기로 승격 예정 — 후보: 마이그레이션 `create_sql.sh` 강제, `@MockBean` 금지, `IntegrationTest` 상속 강제, 백틱 한글 메서드명 금지.

## Links

- `AGENTS.md`, `docs/modules/`, `docs/testing/`, `docs/domains/`
- 가이드: agent-doc-hierarchy-kotlin-spring-guide (PDF)
