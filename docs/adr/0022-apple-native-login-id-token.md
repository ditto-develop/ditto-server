# ADR 0022 — 애플 로그인은 ID 토큰 검증으로만 받고, 네이티브 인증을 별도 추상화로 분리한다

- 상태: Accepted (2026-09-07)
- 근거: discussion(App Store 심사 지침 4.8) + [애플 문서](https://developer.apple.com/documentation/signinwithapple/verifying-a-user)

## Context

iOS 앱을 App Store에 올리려면 심사 지침 4.8에 따라 서드파티 소셜 로그인(카카오)을 제공하는 앱은 **Sign in with Apple도 함께 제공**해야 한다. 서버는 카카오만 지원하고 있었다.

애플은 카카오와 흐름이 다르다.

- 앱이 서버에 주는 것은 액세스 토큰이 아니라 **ID 토큰(JWT)** 이다.
- **사용자 정보 API가 없다.** `sub`·`email`은 토큰의 클레임에서 읽고, 그래서 **서명 검증이 곧 인증**이다.
- 이름은 **최초 인가 1회만** 클라이언트에 전달되고 ID 토큰에는 들어 있지 않다.
- 이메일은 없거나 애플의 릴레이 주소(`@privaterelay.appleid.com`)일 수 있다.

기존 `OAuthClient`(`getAuthorizationUrl`/`getAccessToken`/`getUserInfo`)는 카카오의 리다이렉트 흐름에 맞춘 계약이라, 애플을 여기에 끼우면 세 메서드 중 둘이 "지원하지 않음"이 된다.

## Decision

**네이티브 로그인 인증을 `NativeSocialAuthenticator`로 분리한다.** `OAuthClient`는 리다이렉트 흐름(카카오 웹) 전용으로 남고, 네이티브는 제공자마다 다른 확인 방법을 이 인터페이스가 감춘다 — 카카오는 액세스 토큰으로 me API를 호출하고, 애플은 ID 토큰 서명을 검증한다. 컨트롤러 위쪽(`OAuthFacade`의 회원 생성·제재 판정·토큰 발급)은 두 제공자가 그대로 공유한다.

`POST /api/v1/users/social-login/apple/native`를 추가한다. 응답은 카카오 네이티브와 **같은 스키마**이고, 요청만 다르다(ID 토큰 + 선택적 `rawNonce`·`name`).

ID 토큰 검증(`AppleIdTokenVerifier`)은 다섯 가지를 본다: 애플 JWKS 공개키로 **서명**, `iss`, `aud`(설정한 클라이언트 ID = 앱 번들 ID), `exp`, 그리고 앱이 원본 nonce를 보냈을 때 **`nonce`**(애플에 넘긴 SHA-256 해시와 대조). 애플은 서명 키를 주기적으로 교체하므로 공개키는 TTL 캐시로 두되, **캐시에 없는 `kid`가 오면 한 번 다시 받아온다** — 키 교체 직후의 정상 로그인을 실패로 만들지 않기 위해서다.

### 하지 않기로 한 것

- **인가 코드 교환**: ID 토큰 검증만으로 인증이 성립한다. 코드 교환에는 `.p8` 키로 서명한 클라이언트 시크릿 JWT가 필요한데, refresh token을 쓸 일이 생기기 전까지는 새 시크릿을 들일 이유가 없다. **그래서 이 기능에는 새 비밀값이 하나도 없다** — 검증에 필요한 건 공개된 번들 ID뿐이다.
- **웹 리다이렉트 애플 로그인**: 심사 요건은 앱에만 적용되고, 애플 웹 흐름은 `response_mode=form_post`(POST 콜백)라 지금의 GET 콜백 구조와 맞지 않는다. 웹은 카카오를 유지한다.
- **카카오·애플 계정 연결**: 같은 사람이 두 제공자로 로그인하면 별도 회원이 된다. 이메일로 잇는 방식은 애플 릴레이 주소 때문에 신뢰할 수 없고, 이메일 일치를 계정 병합 근거로 삼는 것 자체가 계정 탈취 경로다. 필요해지면 로그인된 상태에서 명시적으로 잇는 별도 기능으로 다룬다.

## Consequences

- 얻음: App Store 심사 요건을 충족하고, 제공자가 늘어도 `OAuthFacade` 위쪽은 그대로다. 새 비밀값·키 파일을 운영에 들이지 않았다.
- 비용: 로그인 입구가 셋이 됐다(웹 리다이렉트·카카오 네이티브·애플 네이티브). 인증 정책이 바뀌면 세 계약을 함께 봐야 한다(회원 생성·제재·토큰 발급 코드는 하나라 로직 중복은 없다).
- 감수한 것: 애플로 가입하면 이메일이 릴레이 주소이거나 없을 수 있고, 이름도 앱이 최초 1회에 넘겨주지 않으면 비어 있다. 성별·나이는 애초에 애플이 주지 않으므로 카카오 일반 앱과 똑같이 온보딩에서 받는다([ADR 0021](0021-kakao-general-app-profile-input.md)).
- 후속: 앱이 애플 계정 로그인을 붙일 때 **`rawNonce`를 함께 보내는 것을 권장**한다(재생 공격 방어). 서버는 보내지 않아도 통과시키므로 강제는 아니다.

## Links

- 이슈: [#163](https://github.com/ditto-develop/ditto-server/issues/163)
- 핵심 파일: `infrastructure/.../oauth/NativeSocialAuthenticator.kt`(추상화), `infrastructure/.../oauth/apple/AppleIdTokenVerifier.kt`(검증), `infrastructure/.../oauth/config/OAuthConfig.kt`(프로파일별 결선), `api/.../auth/controller/OAuthController.kt`(엔드포인트)
- 관련: [ADR 0019](0019-native-social-login-token-exchange.md) 네이티브 토큰 교환 창구, [ADR 0021](0021-kakao-general-app-profile-input.md) 프로필 정보는 온보딩 입력
