# socialaccount 도메인

소셜 로그인 계정 연결(회원 ↔ 제공자 사용자). **골격 문서** — 불변식·상태전이는 소셜 계정 작업 시 코드 확인 후 채운다.

## 용어
`SocialAccount`(회원과 소셜 제공자 사용자 ID의 연결), `SocialProvider`(제공자 enum — `KAKAO`·`APPLE`).

## 불변식

- 제공자는 `KAKAO`(웹 리다이렉트 + 앱 네이티브)와 `APPLE`(앱 네이티브 전용) 둘이다. **같은 사람이 두 제공자로 로그인하면 회원이 각각 생긴다** — 이메일로 잇지 않는다(애플 릴레이 주소는 신뢰할 수 없고, 이메일 일치를 병합 근거로 삼으면 계정 탈취 경로가 된다). [ADR 0022](../adr/0022-apple-native-login-id-token.md)
- 애플의 `providerUserId`는 ID 토큰의 `sub`다. 앱(팀) 단위로 안정적이라 소셜 계정 키로 쓸 수 있다.

- `(provider, provider_user_id)` 유니크: 같은 제공자 사용자 1명은 회원 1명에만 연결.
- `findByMemberId`가 단건 반환 — 회원당 소셜 계정은 1개다. 제공자를 바꿔 로그인하면 연결이 아니라 새 회원이 된다(위 참조).

- `provider_refresh_token`·`provider_client_id` 는 탈퇴 때 제공자 토큰을 폐기하려고 둔다. 지금은 애플만 채우며, 둘은 항상 함께 저장하고 함께 비운다(`storeProviderToken`·`clearProviderToken`). 우리 서비스의 refresh token(`refresh_token` 테이블)과는 다른 토큰이다. 폐기는 토큰을 받은 client_id(앱은 번들 ID, 웹은 Services ID)로 해야 한다. [ADR 0040](../adr/0040-apple-server-notifications-and-token-revoke.md)

## 상태 전이
- 별도 상태 enum 없음. 한 번 만든 연결(`create`)은 바뀌지 않는다.
- 제공자 토큰: 애플 로그인 때 인가 코드가 오면 교환해 덮어쓴다 → 앱 탈퇴로 폐기에 성공하거나 애플 알림으로 탈퇴하면 비운다. 폐기에 실패하면 남겨 두고, 30일 뒤 완전 삭제 때 계정과 함께 지워진다.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/socialaccount/entity/` (`SocialAccount`, `SocialProvider`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/socialaccount/repository/SocialAccountRepository.kt`
- 로그인/가입 흐름은 `docs/domains/auth.md` 참조.
