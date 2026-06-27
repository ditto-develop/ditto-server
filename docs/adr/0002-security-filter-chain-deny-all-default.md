# ADR 0002 — 다중 SecurityFilterChain로 인증 계층 구성 (매칭 안 된 경로는 deny-all)

- 상태: Accepted
- 근거: code-evidence (SecurityConfig.kt 각 체인 KDoc + 코드 구조)

> 사후 기록 — 커밋·코드에서 재구성(2026-06-27).

## Context

단일 SecurityFilterChain + authorizeHttpRequests 분기로 모든 경로의 인증 요건을 한 곳에서 분기하면, 매처에 빠진 경로가 의도치 않게 허용되거나 분기가 비대해진다. 또 엔드포인트마다 요구 자격이 다르다: actuator는 사이드카(localhost)만, health/docs는 누구나(permitAll), OAuth 시작/콜백은 브라우저 리다이렉트라 X-API-Key 헤더를 실을 수 없어 키 없이 접근해야 하고, 소셜 로그인·토큰 갱신은 아직 JWT가 없는 시점에 호출되며, 나머지 보호 API는 API Key+JWT를 모두 요구한다.

## Decision

securityMatcher 기준으로 요구 자격이 같은 그룹마다 별도 SecurityFilterChain을 `@Order`로 분리한다. 세션은 STATELESS, csrf disable. 현재 6개 체인:

1. actuator — localhost permitAll
2. health/docs/swagger — permitAll
3. publicApi(OAuth 시작·콜백) — permitAll
4. apiKeyOnly(소셜 로그인·토큰 갱신) — API Key만
5. `/api/**` — API Key + JWT (`ApiKeyAuthFilter` 뒤에 `JwtAuthenticationFilter`를 `addFilterAfter`)
6. 그 외 모든 경로 — `anyRequest().denyAll()`

마지막 deny-all 체인으로 어떤 매처에도 안 걸린 경로는 자동 차단(secure-by-default)된다.

## Consequences

- 얻음: 새 경로가 의도치 않게 열리지 않음(매처 미스매치 시 denyAll로 떨어짐), 체인별로 요구 자격이 코드에 명시되어 읽기 쉬움.
- 비용: 체인이 늘면 `@Order` 정렬과 securityMatcher 중복/누락에 주의해야 함(새 공개 경로 추가 시 매처와 deny-all의 상호작용 검토).
- publicApi 체인 신설로 OAuth 콜백이 API Key 검사에서 제외됨.

## Links

- commits: f7d5822(인증/인가 체인 도입), 7999ce8(JWT 필터+체인 분리), 1d5a6a7(publicApi 체인 신설·매처 재정렬)
- `api/.../config/SecurityConfig.kt`(Order 1~6, 각 체인 KDoc), `config/auth/ApiKeyAuthFilter.kt`, `config/auth/JwtAuthenticationFilter.kt`
