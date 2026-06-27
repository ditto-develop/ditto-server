# ADR 0006 — 어드민 인가를 @PreAuthorize가 아닌 JwtAuthenticationFilter 경로 검사로

- 상태: Accepted
- 근거: memory (admin_authz_filter_decision)

> 사후 기록 — 결정 메모리·커밋·코드에서 재구성(2026-06-27).

## Context

어드민 엔드포인트(`/api/v1/admin/**`)에 인가를 걸어야 한다. 일반적 방법은 `@EnableMethodSecurity` + `@PreAuthorize`이지만, 어드민을 향후 별도 모듈/서버로 분리할 계획이 있다. `@PreAuthorize` 방식은 분리 시점에 어노테이션·`@EnableMethodSecurity`·AccessDeniedException 핸들러가 전부 폐기 대상이 되고, 어노테이션 누락 시 보호 구멍이 생긴다.

## Decision

`JwtAuthenticationFilter` 안에서 `/api/v1/admin` prefix 경로는 `member.isAdmin()`이 아니면 403 FORBIDDEN으로 차단한다(`ADMIN_PATH_PREFIX`). 인가 판단 소스는 DB의 `member.role`(필터가 매 요청 회원 로드 중)이며, JWT의 role claim은 FE 표시 전용으로 서버는 인가에 파싱하지 않는다. ADMIN 부여는 운영자 수동 DB UPDATE(이후 어드민 UI로 확장).

## Consequences

- 얻음: secure-by-default(경로 prefix 기반이라 어노테이션 누락 사고 없음), 어드민 모듈 분리 시 필터의 if 블록을 옮기고 경로 조건만 제거하면 끝, 수정 파일 1곳.
- 비용: 인가 규칙이 필터에 집중되어 메서드 시그니처만 봐서는 보호 여부를 알 수 없음(경로 컨벤션 의존).
- 새 어드민 엔드포인트는 `/api/v1/admin` prefix 아래 두면 자동 보호.

## Links

- commit: ee5fec4
- memory: admin_authz_filter_decision (분리 계획·@PreAuthorize 기각 사유)
- `api/.../config/auth/JwtAuthenticationFilter.kt`(ADMIN_PATH_PREFIX + isAdmin→FORBIDDEN), `JwtTokenProvider.kt`(role claim 서버 미파싱), `domain/.../member/entity/MemberRole.kt`, `Member.kt`(role)
