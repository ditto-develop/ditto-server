# socialaccount 도메인

소셜 로그인 계정 연결(회원 ↔ 제공자 사용자). **골격 문서** — 불변식·상태전이는 소셜 계정 작업 시 코드 확인 후 채운다.

## 용어
`SocialAccount`(회원과 소셜 제공자 사용자 ID의 연결), `SocialProvider`(제공자 enum — 현재 `KAKAO`).

## 불변식
- `(provider, provider_user_id)` 유니크: 같은 제공자 사용자 1명은 회원 1명에만 연결.
- `findByMemberId`가 단건 반환 — 회원당 소셜 계정은 사실상 1개 전제. (TODO: 다중 제공자 연결 허용 여부는 정책 확정 후 기술)

## 상태 전이
- 별도 상태 enum 없음. 연결 생성(`create`) 후 변경 없음.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/socialaccount/entity/` (`SocialAccount`, `SocialProvider`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/socialaccount/repository/SocialAccountRepository.kt`
- 로그인/가입 흐름은 `docs/domains/auth.md` 참조.
