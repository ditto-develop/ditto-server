# ADR 0005 — 카카오 동의항목(email·생년월일)을 콜백 쿼리가 아닌 GET /api/v1/users/me로 전달

- 상태: Accepted
- 근거: memory (oauth_signup_info_api_decision, 보안 트레이드오프 명시)

> 사후 기록 — 결정 메모리·커밋·코드에서 재구성(2026-06-27).

## Context

신규 회원가입에 필요한 카카오 동의항목(email, 생년월일)을 FE에 전달해야 한다. 초기에는 콜백 리다이렉트 쿼리 파라미터로 넘기려 했으나, 그러면 PII가 CloudFront/ALB 액세스 로그·브라우저 히스토리·Referer에 평문으로 남고, URL 자체는 인증이 없으며, email·생년월일은 만료 없는 영구 개인정보라는 보안 문제가 있다. ([ADR 0004](0004-oauth-callback-redirect-and-cookie.md)의 쿠키 전환 보안 기조와 연속된 판단.)

## Decision

동의항목을 콜백 URL로 노출하지 않고 `GET /api/v1/users/me` 조회 API로 전달한다(응답은 email·birthDate만, birthDate는 LocalDate). 응답 body는 로그·히스토리·Referer에 남지 않고 Bearer 토큰 보유자만 조회 가능하다. PENDING 회원의 이 경로 접근 허용은 전용 SecurityFilterChain을 새로 만들지 않고 `JwtAuthenticationFilter`에 `pendingAllowedPaths` 파라미터를 추가해 SecurityConfig에서 `setOf("/api/v1/users/me")`를 주입하는 최소 변경으로 처리한다.

## Consequences

- 얻음: PII가 URL·로그에 노출되지 않고 인증된 주체만 조회.
- 비용: FE가 콜백 직후 토큰으로 추가 조회 1회를 해야 함.
- PENDING 허용을 필터 파라미터로 처리해 보호 체인 구조는 그대로 유지. (음력 생년월일은 이후 구분 없이 그대로 저장하도록 변경됨.)

## Links

- commit: 85950f0 (PR #65 / feature/63)
- memory: oauth_signup_info_api_decision (트레이드오프 표·최종 스펙)
- `api/.../config/auth/JwtAuthenticationFilter.kt`(pendingAllowedPaths), `config/SecurityConfig.kt`, `user/dto/MeResponse.kt`, `infrastructure/.../oauth/kakao/KakaoOAuthClient.kt`
