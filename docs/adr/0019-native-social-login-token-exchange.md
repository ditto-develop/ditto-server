# ADR 0019 — 네이티브 소셜 로그인은 리다이렉트를 대체하지 않고 토큰 교환 창구를 따로 연다

- 상태: Accepted (2026-08-28)
- 근거: discussion (FE 위키 [BE-Request-App § D](https://github.com/ditto-develop/ditto-fe/wiki/BE-Request-App)) + code-evidence

## Context

로그인은 브라우저 리다이렉트 전용이었다: FE가 `GET /api/v1/users/social-login/{provider}`로 보내면 카카오 인가 페이지 → 콜백에서 인가 코드를 토큰으로 교환 → FE 콜백 URL로 302([ADR 0004](0004-oauth-callback-redirect-and-cookie.md)). 이 흐름은 앱 웹뷰에서도 그대로 동작한다.

앱이 네이티브 카카오 SDK를 쓰면 카카오톡 앱으로 바로 넘어가는 로그인이 되어 전환율이 오르는데, 이때 앱이 들고 오는 것은 **인가 코드가 아니라 SDK가 이미 발급받은 액세스 토큰**이다. 기존 콜백은 인가 코드만 받고, 응답도 302 리다이렉트라 앱이 쓸 수 없다.

대안으로 콜백을 `Accept` 헤더나 쿼리 파라미터로 분기시켜 JSON도 반환하게 만드는 방법이 있었다. 그러나 웹의 유일한 로그인 경로에 앱 전용 분기를 넣는 셈이라, 한 엔드포인트가 입력(코드/토큰)과 출력(302/JSON)을 모두 두 갈래로 갖게 된다.

## Decision

리다이렉트 엔드포인트는 그대로 두고 `POST /api/v1/users/social-login/kakao/native`를 **추가**한다. 이 엔드포인트는 소셜 액세스 토큰을 받아 사용자 정보를 조회하고 우리 토큰으로 교환해 JSON으로 답한다.

- 회원 조회·생성, 제재 판정, 토큰 발급은 리다이렉트 로그인과 **같은 코드 경로**(`OAuthFacade`)를 탄다. 그래서 `signupRequired`·`sanctioned`의 의미가 콜백 쿼리 계약과 어긋날 수 없다.
- refreshToken은 본문이 아니라 리다이렉트 로그인과 동일하게 HttpOnly 쿠키로 내려간다([ADR 0004](0004-oauth-callback-redirect-and-cookie.md)의 기조 유지).
- 경로에 provider를 고정한다. 네이티브 SDK 계약은 제공자마다 다르므로 `{provider}`로 묶지 않고 제공자별 경로를 연다.
- 보안 체인은 **API Key만 필요한 체인**(`SecurityConfig` @Order(4))에 둔다. 로그인 전 호출이라 JWT가 없고, 브라우저 리다이렉트가 아니라 앱이 직접 호출하므로 헤더를 실을 수 있어 permitAll 체인(@Order(3))보다 한 단계 조인다.
- 제공자가 만료·위조 토큰에 4xx로 답하면 서버 오류(500)가 아니라 `INVALID_SOCIAL_ACCESS_TOKEN`(1002)으로 바꿔 전달한다. 값을 고른 쪽이 클라이언트이기 때문이다. 5xx는 그대로 서버 오류로 남긴다.

## Consequences

- 얻음: 앱이 카카오톡 앱-투-앱 로그인을 쓸 수 있고, 웹 로그인 경로는 손대지 않아 회귀 위험이 없다. 제재·가입 분기가 두 경로에서 같은 코드로 판정된다.
- 비용: 로그인 입구가 둘이 되어, 인증 정책이 바뀌면 두 곳의 계약을 함께 확인해야 한다(코드 경로는 하나라 로직 중복은 없다).
- 남긴 것: 카카오 `access_token_info`의 `app_id` 대조는 넣지 않았다. 다른 카카오 앱에서 발급된 토큰으로 로그인하는 **토큰 치환 공격**이 열려 있다 — 카카오 회원번호는 앱마다 부여되므로 ID가 겹치면 계정이 뒤바뀔 수 있다. 막으려면 `KAKAO_APP_ID` 주입이 필요해 별도 작업으로 다룬다.

## Links

- 이슈: [#157](https://github.com/ditto-develop/ditto-server/issues/157)
- 핵심 파일: `api/.../auth/controller/OAuthController.kt`(엔드포인트), `api/.../auth/facade/OAuthFacade.kt`(두 로그인의 공통 경로), `api/.../auth/service/OAuthService.kt`(토큰→사용자 정보 조회와 4xx 변환), `api/.../config/SecurityConfig.kt`(체인 등록)
