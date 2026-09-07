# socialaccount 도메인

소셜 로그인 계정 연결(회원 ↔ 제공자 사용자). **골격 문서** — 불변식·상태전이는 소셜 계정 작업 시 코드 확인 후 채운다.

## 용어
`SocialAccount`(회원과 소셜 제공자 사용자 ID의 연결), `SocialProvider`(제공자 enum — `KAKAO`·`APPLE`).

## 불변식

- 제공자는 `KAKAO`(웹 리다이렉트 + 앱 네이티브)와 `APPLE`(앱 네이티브 전용) 둘이다. **같은 사람이 두 제공자로 로그인하면 회원이 각각 생긴다** — 이메일로 잇지 않는다(애플 릴레이 주소는 신뢰할 수 없고, 이메일 일치를 병합 근거로 삼으면 계정 탈취 경로가 된다). [ADR 0022](../adr/0022-apple-native-login-id-token.md)
- 애플의 `providerUserId`는 ID 토큰의 `sub`다. 앱(팀) 단위로 안정적이라 소셜 계정 키로 쓸 수 있다.

- `(provider, provider_user_id)` 유니크: 같은 제공자 사용자 1명은 회원 1명에만 연결.
- `findByMemberId`가 단건 반환 — 회원당 소셜 계정은 1개다. 제공자를 바꿔 로그인하면 연결이 아니라 새 회원이 된다(위 참조).

## 상태 전이
- 별도 상태 enum 없음. 연결 생성(`create`) 후 변경 없음.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/socialaccount/entity/` (`SocialAccount`, `SocialProvider`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/socialaccount/repository/SocialAccountRepository.kt`
- 로그인/가입 흐름은 `docs/domains/auth.md` 참조.
