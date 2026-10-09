# ADR 0040 — 애플 서버 간 알림을 탈퇴로 반영하고, 앱 안 탈퇴 때 애플 토큰을 폐기한다

- 상태: Accepted (2026-10-09)
- 근거: discussion(#280, FE 요청서) + 애플 공지(2025-10-09, 한국 개발자 서버 간 알림 필수) + App Store 심사 지침 5.1.1(v)

## Context

두 가지 요건이 한꺼번에 왔다.

1. 2026-01-01부터 한국에 기반한 개발자는 Sign in with Apple 의 서버 간 알림 엔드포인트를 등록해야 한다. 애플은 사용자가 디토와의 연결을 끊거나(`consent-revoked`) Apple 계정 자체를 지우면(`account-delete`) 우리 서버에 서명된 JWS 로 알린다. 이메일 전달 설정 변경(`email-disabled`·`email-enabled`)도 같은 창구로 온다.
2. 앱 안에서 계정을 지우면 애플 토큰도 `auth/revoke` 로 폐기해야 한다. 그런데 [ADR 0022](0022-apple-native-login-id-token.md)는 ID 토큰 검증만 하고 인가 코드를 교환하지 않기로 해서, 폐기할 토큰이 서버에 없었다.

알림으로 오는 두 사건은 애플 쪽에서 이미 끝난 일이라 우리가 막을 수 없다. 반면 우리 탈퇴는 진행 중인 매칭·채팅이 있으면 거부한다(`LeaveProgressChecker`). 상대가 탈퇴자와 같은 방에 남는 상태를 만들지 않기 위해서다.

## Decision

**알림 수신.** `POST /api/v1/users/social-login/apple/notifications` 를 공개 체인([ADR 0002](0002-security-filter-chain-deny-all-default.md)의 permitAll 체인)에 둔다. 애플 서버는 API Key 를 실을 수 없으므로 payload 의 애플 서명 검증이 곧 인증이다. 서명·`iss` 는 ID 토큰과 같은 키 세트(`AppleSignedTokenParser`)로, `aud` 는 우리 client_id 목록으로, 오래된 payload 는 `iat` 로 거른다.

- `account-delete`·`consent-revoked` 는 둘 다 탈퇴로 반영한다. 연결 해제도 애플 안내가 계정 삭제와 같은 처리를 기대하고, 탈퇴는 소프트 삭제라 30일 안에 다시 로그인하면 복구된다([ADR 0016](0016-member-leave-soft-delete-and-restore.md)).
- 진행 중인 매칭·채팅이 있으면 세션만 바로 끊고 탈퇴를 미룬다(`member.deferred_leave_reason`). 스케줄러가 매시 30분에 진행이 끝난 회원을 그 사유로 탈퇴시킨다. 대기 중에 같은 애플 계정으로 다시 로그인하면 대기를 푼다.
- 이미 탈퇴했거나 회원이 없으면 아무것도 하지 않는다. 우리가 탈퇴 때 토큰을 폐기하면 애플이 연결 해제 알림을 되돌려 보내므로 반드시 멱등이어야 한다.
- 이메일 전달 알림은 기록만 한다. 메일을 보내는 기능이 없어 저장해도 읽는 곳이 없다.
- 응답은 프로젝트 규칙대로 항상 HTTP 200 이다. 애플의 재시도 정책을 확인하지 못해 이 경로만 5xx 로 바꾸는 예외를 두지 않았다. 처리 실패는 ERROR 로그로 추적한다.

**토큰 폐기.** 로그인 때 앱이 보낸 인가 코드(네이티브 `authorizationCode`, 웹 콜백 `code`)를 `auth/token` 으로 교환해 애플 refresh token 과 그 토큰을 받은 client_id 를 `social_account` 에 둔다. client_id 는 ID 토큰의 `aud` 이고(앱은 번들 ID, 웹은 Services ID) 폐기도 같은 값으로 해야 한다. 앱 안 탈퇴가 커밋된 뒤 트랜잭션 밖에서 `auth/revoke` 를 부르고, 실패해도 탈퇴는 그대로 둔다. 알림으로 처리하는 탈퇴는 애플 쪽에서 이미 끊겼으므로 폐기하지 않고 저장된 토큰만 지운다.

client_secret 은 팀의 .p8 키로 요청마다 만든다. 키·팀 ID·키 ID 중 하나라도 없으면 교환·폐기를 건너뛰고 경고만 남긴다.

### 하지 않기로 한 것

- **진행 중이어도 바로 탈퇴**: 막는 검사를 건너뛰면 성사된 재매칭·그룹 성사가 탈퇴자가 든 채팅방을 만든다. 지금 코드가 다루지 않는 상태라 미루기로 했다.
- **진행 상태를 강제로 끝내고 탈퇴**: 상대의 매칭을 끊는 새 정책이라 범위가 크다. 필요해지면 따로 다룬다.
- **탈퇴 때 애플 재인증으로 코드를 새로 받기**: 토큰을 보관하지 않아도 되고 기존 가입자도 모두 폐기할 수 있지만, 탈퇴 때 인증 창이 한 번 더 뜨고 FE 가 앱·웹 양쪽에 흐름을 새로 만들어야 한다. FE 변경이 한 줄인 로그인 때 보관을 골랐다.

## Consequences

- 얻음: 애플 정책 두 가지를 충족한다. 밖에서 끝난 계정 삭제가 우리 DB 에도 반영된다.
- 비용: 인증 없는 공개 경로가 하나 늘었다. 로그인마다 애플 `auth/token` 호출이 하나 더 생긴다. 애플 토큰을 DB 에 둔다.
- 감수한 것: 배포 뒤 다시 로그인하지 않은 기존 애플 회원은 토큰이 없어 탈퇴 때 폐기되지 않는다. 진행 중이라 미뤄진 회원은 그동안 상대에게 응답 없는 사람으로 남는다.
- 후속: 배포 전에 `member.deferred_leave_reason`, `social_account.provider_refresh_token`·`provider_client_id` 마이그레이션을 먼저 실행해야 한다(prod 는 `ddl-auto: validate`). 비밀값을 넣은 뒤 애플 개발자 포털에 엔드포인트를 등록한다.

## Links

- 이슈: [#280](https://github.com/ditto-develop/ditto-server/issues/280)
- 핵심 파일: `api/.../auth/controller/AppleServerNotificationController.kt`(수신), `api/.../auth/service/AppleServerNotificationService.kt`(탈퇴 반영), `api/.../user/scheduler/DeferredLeaveScheduler.kt`(미룬 탈퇴), `api/.../auth/service/AppleRefreshTokenService.kt`(교환·폐기), `api/.../user/facade/UserLeaveFacade.kt`(앱 탈퇴 후 폐기), `infrastructure/.../oauth/apple/AppleServerNotificationJwsVerifier.kt`(검증)
- 관련: [ADR 0022](0022-apple-native-login-id-token.md)의 "인가 코드 교환을 하지 않는다"를 이 ADR 이 뒤집는다. [ADR 0016](0016-member-leave-soft-delete-and-restore.md) 탈퇴 소프트 삭제, [ADR 0035](0035-personal-match-member-row-lock.md) 회원 행 잠금 순서
