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
- **카카오는 일반 앱이다 — 받는 동의항목은 `profile_nickname`·`account_email` 둘이다.** 이름·성별·연령대·생일·전화번호는 비즈 앱 전용이라 신청할 수 없고, 이메일은 2026-09-15 일반 앱 상태로 열어 prod 환경변수 `KAKAO_SCOPES`에 추가했다. 요청 scope는 `ditto.oauth.kakao.scopes` 설정값이며(동의항목이 더 열리면 환경변수만 확대), **설정되지 않은 동의항목을 넘기면 카카오가 로그인을 거부**한다. 성별·나이는 온보딩 입력으로 받아 가입 필수값이고, 이름·전화번호·생년월일은 `PATCH /api/v1/users/me/personal-info`(사용자 입력)와 재로그인(`updateOAuthInfo`) 두 경로로 나중에 채운다. 이메일은 로그인·재로그인 때 카카오 값으로 저장·갱신되고(`MemberSocialAccountService.findOrCreateMember`), 미동의 회원은 같은 PATCH로 채울 수 있다. [ADR 0021](../adr/0021-kakao-general-app-profile-input.md)
- 카카오 API 실패는 `KakaoOAuthClient`가 세 갈래로 바꿔 던진다. 토큰 교환이 `invalid_grant`(KOE320, 인가 코드 만료·재사용)면 `INVALID_SOCIAL_AUTH_CODE`(1003, WARN), 사용자 조회가 401이면 `INVALID_SOCIAL_ACCESS_TOKEN`(1002, WARN), 그 외 4xx(client_id·redirect_uri·scope 설정 오류)·5xx·타임아웃은 `SOCIAL_PROVIDER_ERROR`(1004, ERROR). 응답 message는 ErrorCode 기본 문구이고 카카오 원문(error_code·error_description)은 서버 로그에만 남는다. 웹 콜백은 302 경로라 실패 시 브라우저가 API 호스트의 JSON을 보게 되는데, 이건 [#60](https://github.com/ditto-develop/ditto-server/issues/60) 범위 밖으로 두었다.
- 카카오 동의항목(email·생년월일)은 콜백 쿼리가 아니라 `GET /api/v1/users/me`로 전달. PENDING 허용은 `JwtAuthenticationFilter`의 `pendingAllowedPaths`로 처리. [ADR 0005](../adr/0005-kakao-consent-via-me-api.md)
- PENDING 게이트: 회원가입 미완료 회원은 `pendingAllowedPaths` 외 보호 API 접근 시 `SIGNUP_REQUIRED`.
- 탈퇴 게이트: LEFT 회원은 보호 API 접근·토큰 갱신이 모두 `MEMBER_LEFT`(403)로 거부된다. 복구 경로는 재가입(소셜 로그인)뿐이다. `refresh`는 필터를 지나지 않으므로 `AuthService.refresh`에도 같은 게이트가 있다. [ADR 0016](../adr/0016-member-leave-soft-delete-and-restore.md)
- 제재 게이트: SUSPENDED(해제 전)/BANNED 회원은 `suspendedAllowedPaths` 외 보호 API 접근 시 `MEMBER_SUSPENDED`/`MEMBER_BANNED`(403). 해제 예정일 경과한 정지는 통과만(원복은 배치·로그인 — [ADR 0009](../adr/0009-sanction-ssot-and-lazy-expiry.md)). 우회 경로 봉쇄: `AuthService.refresh`(필터 미경유)도 동일 거부, 로그인은 아래 콜백 계약으로 안내.
- 네이티브 앱 로그인: `POST /api/v1/users/social-login/kakao/native`는 소셜 SDK 액세스 토큰을 우리 토큰으로 교환해 JSON으로 답한다. 리다이렉트 로그인의 **추가**이지 대체가 아니며(웹은 그대로), 회원 생성·제재 판정·토큰 발급은 같은 `OAuthFacade` 경로를 타므로 `signupRequired`·`sanctioned` 의미가 콜백 계약과 같다. refreshToken은 동일하게 HttpOnly 쿠키. 체인은 API Key만 필요한 @Order(4). [ADR 0019](../adr/0019-native-social-login-token-exchange.md)
- **애플 로그인은 앱·웹 둘 다 지원한다.** 앱은 `POST /api/v1/users/social-login/apple/native`, 웹은 인가 URL(`GET .../social-login/APPLE`) → 애플 → **`POST /api/v1/users/social-login/APPLE/callback`**(폼 POST). 웹 콜백이 POST인 이유는 scope 를 요청하려면 `response_mode=form_post` 가 필수이기 때문이고, `response_type=code id_token` 으로 받아 **같은 검증기로 ID 토큰만 검증**한다(코드 교환 없음 = 시크릿 없음). 웹은 `client_id` 가 Services ID 다. [ADR 0023](../adr/0023-apple-web-login-form-post-callback.md)
- **CORS 허용 origin에 `https://appleid.apple.com` 이 있어야 애플 웹 로그인이 된다.** 콜백이 애플 도메인이 보내는 크로스사이트 폼 POST 라 Origin 이 애플 것인데, 목록에 없으면 컨트롤러 전에 403 `Invalid CORS request` 로 끊긴다. 프로덕션 값은 `.aws/task-definition.json` 의 `CORS_ALLOWED_ORIGINS` 라 코드에 안 보이니 지울 때 주의. 와일드카드로 열지 않는 이유는 이 검사가 로그인 CSRF(피해자 브라우저로 공격자 계정에 로그인시키기)를 막고 있기 때문이다. [ADR 0030](../adr/0030-apple-web-callback-cors-exemption.md)
- 애플 상세: `POST /api/v1/users/social-login/apple/native`. 애플은 사용자 정보 API가 없어 **ID 토큰(JWT) 서명 검증이 곧 인증**이며(JWKS·`iss`·`aud`·`exp`, 앱이 보내면 `nonce`까지), 인가 코드 교환을 하지 않으므로 새 비밀값이 없다. 이름은 애플이 최초 1회만 주므로 앱이 요청에 실어 보낸다. 카카오 계정과 잇지 않는다(제공자별 별도 회원). [ADR 0022](../adr/0022-apple-native-login-id-token.md)
- 제재 로그인 콜백 계약(FE): 제재 회원 로그인 시 토큰 없이 `?sanctioned=true&sanctionCode=MEMBER_SUSPENDED|MEMBER_BANNED&suspendedUntil=<ISO-8601, 정지만>`으로 리다이렉트. (`OAuthService.getSanctionCallbackUrl`)
- **리프레시 토큰이 없으면 `WarnException`(REFRESH_TOKEN_NOT_FOUND, 2001)이다** — 서버 잘못이 아니라 로그아웃·쿠키 삭제·이미 회전된 토큰으로 정상 도달하는 경로다. `ErrorException`이면 스택트레이스가 ERROR로 남아 진짜 장애처럼 보인다. 만료(`REFRESH_TOKEN_EXPIRED`, 2002)도 같은 등급이다.
- **`UserDetailsServiceAutoConfiguration`은 제외한다**(`DittoApplication`). 인증 주체는 JWT 필터와 어드민 세션이 직접 만들고 `UserDetailsService`를 쓰는 경로가 없는데, 켜 두면 부팅마다 인메모리 계정과 생성 비밀번호가 프로덕션 로그에 찍힌다.
- 어드민 인가는 `@PreAuthorize`가 아니라 `JwtAuthenticationFilter`의 경로 prefix 검사: `/api/v1/admin` 경로는 `member.isAdmin()` 아니면 `403 FORBIDDEN`. [ADR 0006](../adr/0006-admin-authz-filter-path-check.md)
- WebSocket(STOMP): `/ws` 핸드셰이크는 HTTP 계층 permitAll(브라우저 WS 헤더 제약), 인증·인가는 `StompAuthChannelInterceptor`(CONNECT: `X-API-Key`+JWT, SUBSCRIBE: 방 멤버십). deny-all 체인은 `@Order(7)`. [ADR 0009](../adr/0009-websocket-stomp-auth.md)

## 어드민 표면 (코드 확인 — ADR 미반영)
어드민은 두 표면으로 나뉜다.
- `/api/v1/admin/**`(JSON) — 위 `JwtAuthenticationFilter` 경로 검사로 보호. ([ADR 0006](../adr/0006-admin-authz-filter-path-check.md))
- `/admin/**`(Thymeleaf 서버 렌더 UI) — 별도 `AdminSecurityConfig` 체인(`@Order(0)`, 세션 기반, CSRF 활성, `hasRole("ADMIN")`). **카카오·애플 OAuth 로그인**이며 `AdminLoginService`가 기존 회원 매칭 후 `role=ADMIN`만 허용(회원 생성 안 함). 로그인·콜백·정적 리소스만 공개.
- **어드민 권한은 로그인 시점에만 DB를 읽는다.** 세션에 `ROLE_ADMIN` authority 를 심는 것이 인가의 근거이고(`AdminOAuthController`), 이후 요청은 `member.role` 을 다시 보지 않는다. 따라서 DB 의 role 을 바꿔도 **재로그인 전까지는 반영되지 않는다**(반대로 권한을 뺏어도 기존 세션은 만료 전까지 살아 있다).
- 어드민 애플 로그인은 `POST /admin/oauth/apple/callback`(폼 POST)으로 받고, **이 한 경로만 CSRF 예외**다 — 폼 값을 믿지 않고 애플이 서명한 ID 토큰 검증만으로 인증하기 때문이다. 검증기는 앱·유저 웹과 같은 `AppleIdTokenVerifier`. 애플 Return URL 은 https 만 허용해 **로컬에서는 동작하지 않는다**(로컬은 `/admin/oauth/dev`). [ADR 0028](../adr/0028-admin-apple-login-csrf-exemption.md)
- TODO: 두 표면의 책임 경계·향후 모듈 분리 계획을 ADR로 정리(현재 ADR 0006은 `/api/v1/admin` JSON 경로 기준).

## 결정 배경 (ADR)
- [0002](../adr/0002-security-filter-chain-deny-all-default.md) 다중 체인 + deny-all 기본
- [0003](../adr/0003-jwt-subject-member-id.md) JWT subject = memberId
- [0004](../adr/0004-oauth-callback-redirect-and-cookie.md) OAuth 콜백 302 + refresh 쿠키
- [0005](../adr/0005-kakao-consent-via-me-api.md) 카카오 동의항목 me API 전달
- [0006](../adr/0006-admin-authz-filter-path-check.md) 어드민 인가 필터 경로 검사
- [0009](../adr/0009-websocket-stomp-auth.md) WebSocket(STOMP) 인증 — 핸드셰이크 개방 + 프레임 레벨 인증·구독 인가
- [0019](../adr/0019-native-social-login-token-exchange.md) 네이티브 소셜 로그인 토큰 교환 — 리다이렉트 유지 + 앱 전용 창구 추가
- [0021](../adr/0021-kakao-general-app-profile-input.md) 카카오 일반 앱 전제 — 프로필 정보는 온보딩 입력

## 핵심 파일
- 체인 정의: `api/src/main/kotlin/com/ditto/api/config/SecurityConfig.kt` (Order 1~6, 각 체인 KDoc)
- 인증 필터·토큰: `api/src/main/kotlin/com/ditto/api/config/auth/` (`JwtAuthenticationFilter`, `JwtTokenProvider`, `ApiKeyAuthFilter`, `MemberPrincipal`, `RefreshTokenCookieFactory`, `CookieProperties` 등)
- 어드민 보안: `api/src/main/kotlin/com/ditto/api/admin/config/AdminSecurityConfig.kt`, `admin/auth/` (`AdminLoginService`, `AdminOAuthController`, `AdminPrincipal`)
