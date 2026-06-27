# refreshtoken 도메인

리프레시 토큰(회원 세션 갱신용). **골격 문서** — 불변식·상태전이는 토큰 작업 시 코드 확인 후 채운다.

## 용어
`RefreshToken`(회원 ID + 토큰 값(UUID) + 만료 일시).

## 불변식
- `token` 유니크. 토큰 값은 UUID(`length = 36`).
- `isExpired(now)`는 `expiresAt < now`로 판정 — 만료 시각 이전까지만 유효.
- 회원당 토큰 개수 정책: `deleteAllByMemberId`로 회원 단위 전량 삭제 가능. (TODO: 발급 시 기존 토큰 회전/단일 유지 여부는 발급 로직 확인 후 기술)

## 상태 전이
- 별도 상태 enum 없음. 생성(`create`) → 만료(`expiresAt` 경과) 또는 삭제.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/refreshtoken/entity/RefreshToken.kt`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/refreshtoken/repository/` (+ `querydsl/`)
- 발급/갱신/로그아웃 흐름은 `docs/domains/auth.md` 참조.
