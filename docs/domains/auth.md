# auth (인증·보안)

인증·인가·어드민 보호. 규칙 **전문은 ADR**(0002~0006)에 있고 여기선 요약+링크만 둔다.

## 용어
- `JWT` — access(짧음, Bearer 헤더) / refresh(HttpOnly 쿠키). subject = 내부 `memberId`. role claim은 FE 표시 전용(서버 인가에 미파싱).
- `ApiKey` — `X-API-Key` 헤더. 보호 API는 ApiKey+JWT 둘 다 요구.
- `MemberPrincipal` — JWT 인증 주체. `{memberId}` 단일 필드. (`config/auth/MemberPrincipal.kt`)
- `MemberStatus` — `PENDING`(소셜 로그인만 완료) / `ACTIVE`(회원가입 완료) / `SUSPENDED` / `BANNED` / `LEFT`(탈퇴). (`domain/.../member/entity/MemberStatus.kt`)
- `MemberRole` — `USER` / `ADMIN`. 인가 판단 소스. (`MemberRole.kt`)
- `AdminPrincipal` — `/admin/**` Thymeleaf UI 세션 주체. `{memberId, name, email}`. (`admin/auth/AdminPrincipal.kt`)

## 핵심 규칙·불변식 (요약 — 전문은 ADR)
- 다중 `SecurityFilterChain`, 매칭 안 된 경로는 마지막 체인 `anyRequest().denyAll()`로 자동 차단(secure-by-default). [ADR 0002](../adr/0002-security-filter-chain-deny-all-default.md)
- JWT subject = 내부 `memberId`(소셜 식별자 결합 제거), role claim은 서버가 인가에 쓰지 않음. [ADR 0003](../adr/0003-jwt-subject-member-id.md)
- OAuth 콜백은 FE로 302 리다이렉트, refreshToken은 HttpOnly·Secure·SameSite 쿠키로만 전달(URL·로그·히스토리 노출 차단). [ADR 0004](../adr/0004-oauth-callback-redirect-and-cookie.md)
- 카카오 동의항목(email·생년월일)은 콜백 쿼리가 아니라 `GET /api/v1/users/me`로 전달. PENDING 허용은 `JwtAuthenticationFilter`의 `pendingAllowedPaths`로 처리. [ADR 0005](../adr/0005-kakao-consent-via-me-api.md)
- PENDING 게이트: 회원가입 미완료 회원은 `pendingAllowedPaths` 외 보호 API 접근 시 `SIGNUP_REQUIRED`.
- 탈퇴 게이트: LEFT 회원은 보호 API 접근·토큰 갱신이 모두 `MEMBER_LEFT`(403)로 거부된다. 복구 경로는 재가입(소셜 로그인)뿐이다. `refresh`는 필터를 지나지 않으므로 `AuthService.refresh`에도 같은 게이트가 있다. [ADR 0016](../adr/0016-member-leave-soft-delete-and-restore.md)
- 제재 게이트: SUSPENDED(해제 전)/BANNED 회원은 `suspendedAllowedPaths` 외 보호 API 접근 시 `MEMBER_SUSPENDED`/`MEMBER_BANNED`(403). 해제 예정일 경과한 정지는 통과만(원복은 배치·로그인 — [ADR 0009](../adr/0009-sanction-ssot-and-lazy-expiry.md)). 우회 경로 봉쇄: `AuthService.refresh`(필터 미경유)도 동일 거부, 로그인은 아래 콜백 계약으로 안내.
- 제재 로그인 콜백 계약(FE): 제재 회원 로그인 시 토큰 없이 `?sanctioned=true&sanctionCode=MEMBER_SUSPENDED|MEMBER_BANNED&suspendedUntil=<ISO-8601, 정지만>`으로 리다이렉트. (`OAuthService.getSanctionCallbackUrl`)
- 어드민 인가는 `@PreAuthorize`가 아니라 `JwtAuthenticationFilter`의 경로 prefix 검사: `/api/v1/admin` 경로는 `member.isAdmin()` 아니면 `403 FORBIDDEN`. [ADR 0006](../adr/0006-admin-authz-filter-path-check.md)
- WebSocket(STOMP): `/ws` 핸드셰이크는 HTTP 계층 permitAll(브라우저 WS 헤더 제약), 인증·인가는 `StompAuthChannelInterceptor`(CONNECT: `X-API-Key`+JWT, SUBSCRIBE: 방 멤버십). deny-all 체인은 `@Order(7)`. [ADR 0009](../adr/0009-websocket-stomp-auth.md)

## 어드민 표면 (코드 확인 — ADR 미반영)
어드민은 두 표면으로 나뉜다.
- `/api/v1/admin/**`(JSON) — 위 `JwtAuthenticationFilter` 경로 검사로 보호. ([ADR 0006](../adr/0006-admin-authz-filter-path-check.md))
- `/admin/**`(Thymeleaf 서버 렌더 UI) — 별도 `AdminSecurityConfig` 체인(`@Order(0)`, 세션 기반, CSRF 활성, `hasRole("ADMIN")`). 카카오 OAuth 로그인이며 `AdminLoginService`가 기존 회원 매칭 후 `role=ADMIN`만 허용(회원 생성 안 함). 로그인·콜백·정적 리소스만 공개.
- TODO: 두 표면의 책임 경계·향후 모듈 분리 계획을 ADR로 정리(현재 ADR 0006은 `/api/v1/admin` JSON 경로 기준).

## 결정 배경 (ADR)
- [0002](../adr/0002-security-filter-chain-deny-all-default.md) 다중 체인 + deny-all 기본
- [0003](../adr/0003-jwt-subject-member-id.md) JWT subject = memberId
- [0004](../adr/0004-oauth-callback-redirect-and-cookie.md) OAuth 콜백 302 + refresh 쿠키
- [0005](../adr/0005-kakao-consent-via-me-api.md) 카카오 동의항목 me API 전달
- [0006](../adr/0006-admin-authz-filter-path-check.md) 어드민 인가 필터 경로 검사
- [0009](../adr/0009-websocket-stomp-auth.md) WebSocket(STOMP) 인증 — 핸드셰이크 개방 + 프레임 레벨 인증·구독 인가

## 핵심 파일
- 체인 정의: `api/src/main/kotlin/com/ditto/api/config/SecurityConfig.kt` (Order 1~6, 각 체인 KDoc)
- 인증 필터·토큰: `api/src/main/kotlin/com/ditto/api/config/auth/` (`JwtAuthenticationFilter`, `JwtTokenProvider`, `ApiKeyAuthFilter`, `MemberPrincipal`, `RefreshTokenCookieFactory`, `CookieProperties` 등)
- 어드민 보안: `api/src/main/kotlin/com/ditto/api/admin/config/AdminSecurityConfig.kt`, `admin/auth/` (`AdminLoginService`, `AdminOAuthController`, `AdminPrincipal`)
