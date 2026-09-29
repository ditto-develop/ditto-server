# ADR 0034 — refresh 토큰 회전에 30초 유예를 둔다

- 상태: Accepted
- 근거: Issue #233 (앱에서 푸시를 눌러 들어가면 로그인이 풀린다는 제보. 서버 로그에서 같은 refresh 토큰이 성공 직후 다시 쓰여 2001로 실패하고 곧바로 재로그인하는 흐름 확인)

## Context

`AuthService.refresh`는 받은 토큰을 즉시 삭제하고 새 토큰을 `Set-Cookie`로 내려줬다. 이 방식에서는 새 쿠키가 담긴 응답을 클라이언트가 받지 못하면 그 기기의 세션이 바로 끝난다.

앱(Capacitor 웹뷰)에서는 이런 일이 자주 생긴다.

- FE는 `/`·`/home`에 들어올 때마다 토큰 만료와 상관없이 refresh로 세션을 검증한다. 콜드 스타트는 항상 `/`로 열린다.
- FE가 정적 export라 채팅방 같은 동적 경로는 `router.push`를 해도 문서를 새로 불러온다. 이때 진행 중이던 refresh 응답을 잃는다.
- 새 문서는 옛 쿠키를 그대로 들고 다시 refresh하고, 이미 지워진 토큰이라 실패한다. refresh 요청을 하나로 묶는 FE 장치(single-flight)는 JS 메모리에 있어서 문서가 바뀌면 소용이 없다.

로그상 재사용 간격은 대부분 0.1초 안쪽이거나 수 초였다.

검토한 대안:

- **FE에서 딥링크 이동 전 refresh를 기다린다**: 푸시 경로만 막는다. 로그인 직후처럼 다른 문서 전환에서 생기는 같은 문제는 남는다.
- **동적 경로를 쿼리 방식으로 바꿔 문서 새로고침을 없앤다**: 근본 해결이지만 FE 라우팅 전체를 바꾸는 큰 작업이다.

## Decision

**refresh 때 옛 토큰을 지우지 않고 만료 시각을 지금+30초로 당긴다(`RefreshToken.shortenExpiry`).** 이미 그보다 이르면 늦추지 않는다.

- 30초 안에 옛 토큰이 다시 오면 기존 만료 검사를 그대로 통과해 새 토큰을 발급한다. 유예 전용 분기를 따로 두지 않는다.
- 30초가 지나면 `REFRESH_TOKEN_EXPIRED`(2002)이고, 행이 정리된 뒤에는 `REFRESH_TOKEN_NOT_FOUND`(2001)다.
- 회전마다 만료 행이 쌓이지 않도록 refresh 때 그 회원의 만료된 토큰을 함께 지운다(`deleteExpiredByMemberId`).
- 로그아웃·제재·탈퇴의 회원 단위 삭제(`deleteAllByMemberId`)는 그대로라, 유예 중인 토큰도 즉시 무효가 된다.
- 30초는 로그에서 본 간격(최대 수 초)에 여유를 둔 값이다. 인증 서비스들이 같은 목적으로 두는 유예(0~60초)와 같은 범위다.

## Consequences

- 얻음: 응답 유실·동시 갱신으로 세션이 끊기지 않는다. FE 구조(문서 새로고침, 문서마다 하는 세션 검증)를 바꾸지 않고 서버 한 곳에서 막는다. 스키마 변경이 없다.
- 비용: 탈취된 refresh 토큰을 정상 회전 뒤에도 최대 30초 더 쓸 수 있다.
- 비용: 만료 뒤 옛 토큰의 에러 코드가 2002 또는 2001로 갈린다. FE는 둘 다 실패로 처리하므로 동작 차이는 없다.
- 후속: 유예보다 훨씬 늦게 옛 토큰이 오는 경우(응답을 받고도 쿠키가 저장되지 않은 경우로 추정)는 막지 못한다. 따로 조사한다.

## Links

- 핵심 파일: `api/.../auth/service/AuthService.kt`(`refresh`, `ROTATION_GRACE`), `domain/.../refreshtoken/entity/RefreshToken.kt`(`shortenExpiry`), `domain/.../refreshtoken/repository/querydsl/RefreshTokenRepositoryImpl.kt`(`deleteExpiredByMemberId`)
- 관련: [ADR 0004](0004-oauth-callback-redirect-and-cookie.md) refresh 쿠키 전달
