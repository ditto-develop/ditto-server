# ADR 0023 — 애플 웹 로그인은 폼 POST 콜백으로 받고, ID 토큰을 그 자리에서 검증한다

- 상태: Accepted (2026-09-07)
- 근거: discussion + 애플 문서([Request an authorization](https://developer.apple.com/documentation/signinwithapplerestapi))

## Context

[ADR 0022](0022-apple-native-login-id-token.md)에서 애플 **네이티브(앱)** 로그인만 넣고 웹은 미뤘다 — "심사 요건은 앱에만 적용되고, 애플 웹은 POST 콜백이라 지금 GET 콜백 구조와 맞지 않는다". 이제 웹에서도 애플 로그인을 제공한다.

애플 웹은 카카오 웹과 세 군데가 다르다.

- `scope`(name·email)를 요청하려면 **`response_mode=form_post`가 필수**다. 그래서 콜백이 `GET ?code=...`가 아니라 **`POST`(`application/x-www-form-urlencoded`)** 로 온다.
- 콜백 폼에는 `code`·`id_token`·`state`·`user`가 실린다. `response_type=code id_token`으로 요청하면 **ID 토큰이 콜백에 함께 오므로 인가 코드를 교환할 필요가 없다.**
- `client_id`가 앱 번들 ID가 아니라 **Services ID**다.
- 이름은 ID 토큰이 아니라 `user` 폼 필드의 JSON에, **최초 인가 1회만** 온다.

## Decision

`POST /api/v1/users/social-login/apple/callback`을 추가한다. 응답은 **카카오 콜백과 같은 계약**이다 — FE 콜백 URL로 302, accessToken·signupRequired는 쿼리, refreshToken은 HttpOnly 쿠키([ADR 0004](0004-oauth-callback-redirect-and-cookie.md)).

- **검증은 네이티브와 같은 `AppleIdTokenVerifier`를 쓴다.** 같은 ID 토큰이고 확인할 것도 같다. `aud`만 다르므로(Services ID) 허용 목록에 더한다. 인가 코드는 받아도 쓰지 않으며, 따라서 **클라이언트 시크릿(.p8)은 웹에서도 필요 없다.**
- 인가 URL 생성을 `SocialAuthorizationUrlProvider`로 떼어낸다. 애플 웹은 인가 URL은 필요하지만 코드 교환·userinfo는 하지 않아, `OAuthClient`를 구현하면 두 메서드가 "지원하지 않음"이 된다(ADR 0022와 같은 이유).
- `OAuthFacade`는 리다이렉트 결과 조립(`redirectLoginResult`)을 코드 흐름과 공유한다. 회원 생성·제재 판정·토큰 발급은 세 입구(카카오 웹·카카오 앱·애플 앱/웹)가 모두 같은 코드를 탄다.
- `user` 필드는 `AppleUserFieldReader`가 읽고, **형식이 어긋나도 로그인을 막지 않는다** — 이름은 `PATCH /api/v1/users/me/personal-info`로도 채울 수 있다([ADR 0021](0021-kakao-general-app-profile-input.md)).

## Consequences

- 얻음: 웹에서도 애플 로그인이 되고, 앱과 검증 코드를 공유한다. 새 비밀값은 여전히 없다.
- 비용: 콜백이 두 형태(GET 쿼리 / POST 폼)가 됐다. 제공자를 늘릴 때 어느 쪽인지 먼저 확인해야 한다.
- **알려진 공백**: `state` 파라미터를 쓰지 않는다 — 카카오 웹 로그인도 지금 쓰지 않는다. 로그인 CSRF(공격자 계정으로 피해자 브라우저를 로그인시키는 것)를 막으려면 두 제공자에 함께 넣어야 하고, 애플 콜백은 크로스사이트 POST라 state 쿠키가 `SameSite=None; Secure`여야 한다. 이 PR 범위를 넘으므로 후속 이슈로 분리한다.
- 운영: **Services ID와 Return URL을 애플 개발자 콘솔에 등록**해야 동작한다(`APPLE_WEB_CLIENT_ID`·`APPLE_WEB_REDIRECT_URI`, 그리고 `APPLE_CLIENT_IDS`에 Services ID 추가). 미설정이면 인가 URL의 `client_id`가 비어 애플이 거부한다.
- 애플은 **최초 인가 때 요청한 scope 만큼만** 이후에도 준다. 처음부터 `name email`을 요청하는 이유다 — 나중에 넓혀도 기존 사용자의 이메일은 받을 수 없다.

## Links

- 이슈: [#165](https://github.com/ditto-develop/ditto-server/issues/165)
- 핵심 파일: `infrastructure/.../oauth/SocialAuthorizationUrlProvider.kt`, `infrastructure/.../oauth/apple/AppleWebAuthorizationUrlProvider.kt`, `api/.../auth/controller/OAuthController.kt`(POST 콜백), `api/.../auth/service/AppleUserFieldReader.kt`
