# ADR 0028 — 어드민 애플 로그인은 폼 POST 콜백 한 경로만 CSRF 예외로 둔다

- 상태: Accepted (2026-09-16)
- 근거: 이슈 #185 + [ADR 0023](0023-apple-web-login-form-post-callback.md)

## Context

어드민(`/admin/**`)은 카카오 OAuth 로그인만 지원했다. 그런데 `AdminLoginService`는 소셜 계정으로 회원을 찾는다 —
애플로 가입한 회원은 `social_account`가 `APPLE`로만 연결돼 있어 카카오 콜백의 `findMemberBySocial(KAKAO, ...)`에서
`등록되지 않은 회원입니다`로 거부된다. **`member.role`을 ADMIN으로 올려도 그 앞단에서 막히므로 우회할 방법이 없다.**

여기에 어드민 체인만의 제약이 겹친다.

- 어드민 체인(`AdminSecurityConfig`)은 api 체인들과 달리 **CSRF가 활성**이다(서버 렌더 폼이라 토큰을 자동으로 싣는다).
- 애플 웹은 `scope`를 요청하면 **`response_mode=form_post`가 강제**라 콜백이 외부에서 오는 POST다([ADR 0023](0023-apple-web-login-form-post-callback.md)).
- 그 POST에는 우리 CSRF 토큰이 있을 수 없다. 그대로 두면 콜백이 403으로 막힌다.

## Decision

어드민에 애플 로그인을 추가하되, **CSRF 예외는 `/admin/oauth/apple/callback` 한 경로로 한정**한다.

- 이 경로는 폼 값을 신뢰하지 않는다. `id_token`만 읽어 **애플이 서명한 ID 토큰 검증**으로 인증하므로,
  공격자가 폼을 위조해도 유효한 애플 서명을 만들 수 없다. 나머지 어드민 폼은 CSRF 보호를 그대로 유지한다.
- 검증기는 앱·유저 웹과 **같은 `AppleIdTokenVerifier`**다. 어드민이라고 다른 신뢰 근거를 쓰지 않는다.
  구현은 `NativeSocialAuthenticatorFactory`(APPLE)를 그대로 재사용한다 — 프로파일별 fake/real 분기가 이미 거기 있다.
- `AdminLoginService`를 제공자 중립으로 바꾼다. 제공자마다 다른 것은 **소셜 식별자를 얻는 방법**뿐이고
  (카카오=코드 교환, 애플=ID 토큰 검증), 그 뒤 회원 매칭·`isAdmin()` 판정·`AdminPrincipal` 생성은 `authorizeAdmin()` 하나로 모은다.
- 어드민 Return URL은 유저 웹과 **Services ID는 공유하고 주소만 다르다**(`admin-web-redirect-uri`).
  카카오의 `admin-redirect-uri`와 같은 패턴이다.
- 어드민은 **회원을 생성하지 않으므로** 애플 `user` 폼 필드(최초 인가 1회만 오는 이름·이메일)를 읽지 않는다.

## Consequences

- 얻음: 애플로 가입한 관리자가 어드민에 들어갈 수 있다. 새 비밀값은 없다(코드 교환을 하지 않으므로 .p8 불필요).
- 비용: 어드민 체인에 CSRF 예외가 하나 생겼다. 늘리지 않도록 경로를 `AdminSecurityConfig.APPLE_CALLBACK_PATH` 상수로 고정하고,
  CSRF 없이 통과하는지를 `AdminWebTest`가 회귀로 지킨다.
- **로컬에서 테스트할 수 없다.** 애플은 Return URL로 https만 받고 `localhost`를 거부한다. 로컬 어드민 로그인은
  기존 `/admin/oauth/dev`를 계속 쓴다.
- 운영: `APPLE_ADMIN_REDIRECT_URI` 주입 + **애플 개발자 콘솔의 Services ID에 어드민 Return URL을 추가 등록**해야 동작한다.
  미등록이면 애플이 인가 단계에서 거부한다.
- **알려진 공백**: [ADR 0023](0023-apple-web-login-form-post-callback.md)과 같이 `state`를 쓰지 않는다. 어드민도 같은 공백을 물려받는다.

## Links

- 이슈: [#185](https://github.com/ditto-develop/ditto-server/issues/185)
- 핵심 파일: `api/.../admin/auth/AdminLoginService.kt`, `api/.../admin/auth/AdminOAuthController.kt`(POST 콜백),
  `api/.../admin/config/AdminSecurityConfig.kt`(CSRF 예외), `api/.../admin/config/AdminOAuthClientConfig.kt`
