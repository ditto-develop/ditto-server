# ADR 0021 — 카카오 일반 앱을 전제하고, 프로필 정보는 온보딩에서 받는다

- 상태: Accepted (2026-09-04)
- 근거: discussion(비즈 앱 전환 불가) + 카카오 공식 문서([개인정보 동의항목](https://developers.kakao.com/docs/ko/kakaologin/utilize)) + 피그마 `1.1 인증 & 온보딩`

## Context

카카오 로그인의 **개인정보 동의항목은 비즈 앱(사업자 정보 등록)이라야 신청할 수 있다.** 일반 앱이 기본 제공받는 것은 닉네임·프로필 사진뿐이고, 이메일조차 비즈 앱이 필요하다. 비즈 앱 전환이 불가한 상태로 서비스해야 한다.

그런데 코드는 정반대를 가정하고 있었다.

- `KakaoOAuthClient`가 `account_email·birthyear·birthday·name·phone_number·gender` 6개를 인가 URL의 `scope`로 실었다. **앱에 설정되지 않은 동의항목을 넘기면 카카오가 로그인을 거부하므로 로그인 자체가 깨진다.**
- `CreateUserRequest`의 `gender`·`age`가 optional이었다. 그런데 `MatchmakingService`는 성별·나이가 null인 회원을 후보 풀에서 제외하고(`:122`), `OneToOneMatchingProcessor`는 나이차를 하드 필터로 쓴다(`:53`). 즉 값 없이 가입하면 **가입은 성공하는데 매칭만 영영 안 되는** 회원이 조용히 생긴다.

확인해 보니 화면은 처음부터 직접 입력받는 설계였다. 피그마 `1.3.2 회원가입 _ 프로필 작성`에 `<성별>`(남자/여자)·`<나이>`(20~24 … 60 이상) 선택 UI가 명세돼 있고 FE도 그렇게 구현·필수 검증하고 있다. 카카오 값은 폼 prefill 용도였다.

## Decision

**카카오에서 받는 것은 로그인 식별자뿐이라고 보고, 서비스에 필요한 값은 우리 화면에서 받는다.**

- 요청 `scope`를 설정값(`ditto.oauth.kakao.scopes`)으로 빼고 기본값을 `profile_nickname` 하나로 둔다. 앱 종류에 따라 달라지는 값을 코드에 박지 않는다 — 비즈 앱으로 전환되면 환경변수(`KAKAO_SCOPES`)만 늘리면 된다. 비우면 `scope` 없이 요청해 앱 설정을 그대로 따른다.
- `gender`·`age`를 **가입 필수**로 올린다(`age`는 20~100). 매칭의 입력값이라 없으면 기능이 조용히 죽는 값은 입구에서 막는다.
- 카카오가 주지 않는 신원 정보(이름·전화번호·이메일·생년월일)는 **나중에 얼마든지 채울 수 있게** 두 경로를 연다.
  1. `PATCH /api/v1/users/me/personal-info` — 사용자 입력. 부분 갱신이며 횟수 제한이 없다. `/users/me`와 경로를 분리한 이유는 `JwtAuthenticationFilter`가 HTTP method를 무시하고 경로만으로 PENDING 허용을 판정하기 때문이다(같은 경로에 두면 가입 미완료 회원에게도 열린다).
  2. 재로그인 자동 보완 — `Member.updateOAuthInfo`가 `birthDate`까지 받도록 넓혔다. 비즈 앱 전환 후 동의항목을 열면 회원은 **재로그인만으로** 값이 채워지고, 미동의 항목은 null로 와서 기존 값을 덮지 않는다.

## Consequences

- 얻음: 비즈 앱 없이 서비스가 성립한다. 매칭에 필요한 값은 가입 입구에서 보장되고, 나머지는 시점 제약 없이 보완할 수 있다. 비즈 앱 전환은 코드 변경 없이 설정만으로 되돌린다.
- 비용: 성별·나이가 카카오 검증값이 아닌 **자가신고**가 된다. 신고·제재 대응 시 신원 식별 근거(이름·전화번호)가 기본적으로 없다 — 피그마 `1.2 로그인`의 PASS 본인인증이 원래 그 경로이나 "순서 및 방법 미정, 화면만 구현" 상태다.
- 계약 변경: `POST /api/v1/users`가 `gender`·`age` 없이 오면 이제 `0001`(400)이다. FE는 이미 두 값을 필수로 막고 있어 영향이 없다.

## Links

- 이슈: [#161](https://github.com/ditto-develop/ditto-server/issues/161)
- 핵심 파일: `infrastructure/.../kakao/KakaoOAuthProperties.kt`·`KakaoOAuthClient.kt`(scope 설정화), `api/.../user/dto/CreateUserRequest.kt`(필수화), `api/.../user/controller/UserController.kt`·`service/UserService.kt`(신원 정보 보완), `domain/.../member/entity/Member.kt`(`updatePersonalInfo`·`updateOAuthInfo`)
