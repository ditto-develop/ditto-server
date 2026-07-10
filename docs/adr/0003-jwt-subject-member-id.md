# ADR 0003 — JWT subject를 내부 memberId로 고정 (소셜 식별자 결합 제거)

- 상태: Accepted
- 근거: code-evidence (커밋 제목 'SocialAccount 조회 제거' + diff 구조)

> 사후 기록 — 커밋·코드에서 재구성(2026-06-27).

## Context

초기 access 토큰은 subject에 (providerUserId, provider)를 담았고, 매 요청마다 그 값으로 `SocialAccountRepository`를 조회해 내부 memberId를 얻어야 했다. 인증 주체가 외부 소셜 식별자에 묶여 있어 provider별 조회 로직이 인증 경로에 항상 끼었다.

## Decision

access 토큰 subject에 내부 memberId만 담고 provider claim을 제거한다. `MemberPrincipal`을 {providerUserId, provider}에서 {memberId} 단일 필드로 축소하고, 토큰 소비 지점에서 SocialAccount 조회를 없애 memberId를 직접 사용한다. (role claim은 FE 표시용으로만 존재하며 서버는 인가에 파싱하지 않는다 — [ADR 0006](0006-admin-authz-filter-path-check.md) 참조.)

## Consequences

- 얻음: 매 요청 SocialAccount 조회 제거(인증 경로 단순화·DB 부하 감소), 인증 주체가 외부 provider와 분리되어 결합도 하락.
- 비용: 토큰만으로는 어떤 소셜 계정으로 로그인했는지 알 수 없어 필요 시 memberId로 별도 조회해야 함.
- 이후 회원가입 API도 바디의 provider/providerUserId 대신 토큰 memberId 기반으로 전환됨(8a70d31).

## Links

- commit: 0d376c8
- `api/.../config/auth/JwtTokenProvider.kt`(generateAccessToken(memberId), CLAIM_PROVIDER/getProvider 제거), `config/auth/MemberPrincipal.kt`(memberId 단일 필드)
