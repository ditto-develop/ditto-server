# ADR 0041 — App Store 심사용 데모 로그인은 정해 둔 소셜 계정 하나로만 연다

- 상태: Accepted (2026-10-13)
- 근거: discussion (#305)

## Context

앱 로그인은 카카오·애플 소셜 로그인뿐이다. App Store 심사(가이드라인 2.1)는 로그인이 필요한 앱에 데모 계정을 요구한다. 그런데 소셜 로그인은 계정 정보를 알려 줘도 리뷰어가 카카오·애플 인증을 직접 통과해야 한다. 카카오는 해외 IP 로그인에 추가 인증을 걸 수 있어 심사가 막힐 수 있다.

레포가 공개라 `.aws/task-definition.json` 의 환경변수 값은 모두 공개된다.

## Decision

`POST /api/v1/users/review-login` 을 연다. ID·비밀번호가 맞으면 고정 소셜 계정(`APPLE` / `app-store-review`) 하나로 로그인한 것처럼 처리한다.

- 인증만 다르고 회원 조회·생성, 탈퇴 복구, 제재 판정, 토큰 발급은 네이티브 소셜 로그인과 같은 `OAuthFacade` 경로를 탄다. 응답도 같아서 앱이 결말 분기를 그대로 쓴다.
- 처음 로그인하면 가입 대기 회원이 생긴다. 심사 전에 우리가 한 번 로그인해 온보딩을 마쳐 둔다.
- 대상을 memberId 가 아니라 소셜 계정으로 정한 이유: 미리 회원을 만들고 id 를 찾아 넣는 준비가 없어지고, 리뷰어가 탈퇴를 눌러 본 뒤 다시 로그인하면 소셜 재로그인처럼 복구된다.
- 비밀번호는 BCrypt 해시(`REVIEW_LOGIN_PASSWORD_HASH`)로만 설정한다. 평문은 레포에 두지 않는다.
- `REVIEW_LOGIN_ID`·`REVIEW_LOGIN_PASSWORD_HASH` 중 하나라도 비면 꺼진다. 꺼짐·ID 불일치·비밀번호 불일치는 모두 `INVALID_REVIEW_LOGIN`(1005)이다. ID가 틀려도 비밀번호를 대조해 응답 시간 차이를 줄인다.
- 보안 체인은 네이티브 로그인과 같은 API Key 전용 체인(@Order(4))이다.

## Consequences

- 얻음: 리뷰어가 소셜 인증 없이 앱 전체를 볼 수 있다. 앱 코드는 응답 처리를 새로 짜지 않는다.
- 비용: 비밀번호가 새면 누구나 심사 계정으로 들어온다. 영향은 그 계정 하나이고, 환경변수를 비우면 바로 꺼진다. 실패 횟수 제한은 두지 않았다 — 24자 랜덤 비밀번호를 전제로 한다.
- 후속: 비밀번호를 바꾸려면 새 해시로 `REVIEW_LOGIN_PASSWORD_HASH` 를 바꾸고 App Store Connect 의 심사 정보도 함께 바꾼다.

## Links

- #305, [ADR 0019](0019-native-social-login-token-exchange.md), [ADR 0022](0022-apple-native-login-id-token.md)
- 핵심 파일: `api/.../auth/service/ReviewLoginService.kt`, `api/.../auth/facade/OAuthFacade.kt`(`loginForReview`), `api/.../config/ReviewLoginProperties.kt`
