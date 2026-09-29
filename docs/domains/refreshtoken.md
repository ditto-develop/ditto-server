# refreshtoken 도메인

리프레시 토큰(회원 세션 갱신용).

## 용어
`RefreshToken`(회원 ID + 토큰 값(UUID) + 만료 일시).

## 불변식
- `token` 유니크. 토큰 값은 UUID(`length = 36`).
- `isExpired(now)`는 `expiresAt < now`로 판정 — 만료 시각 이전까지만 유효.
- 회원당 토큰은 여러 개다(기기·로그인마다 발급). 로그아웃·제재·탈퇴는 `deleteAllByMemberId`로 회원 단위 전량 삭제한다.
- **회전**: refresh 때 옛 토큰을 지우지 않고 만료를 지금+30초로 당긴다(`shortenExpiry`, 늦추지는 않는다). 30초 안에 다시 오면 새 토큰을 또 발급한다 — 응답 유실·동시 갱신으로 세션이 끊기지 않게. 같은 refresh에서 그 회원의 만료된 토큰을 지운다(`deleteExpiredByMemberId`). [ADR 0034](../adr/0034-refresh-token-rotation-grace.md)

## 상태 전이
- 별도 상태 enum 없음. 생성(`create`) → (refresh로 회전되면 만료가 지금+30초로 앞당겨짐) → 만료(`expiresAt` 경과) → 다음 refresh 때 정리, 또는 회원 단위 삭제.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/refreshtoken/entity/RefreshToken.kt`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/refreshtoken/repository/` (+ `querydsl/`)
- 발급/갱신/로그아웃 흐름은 `docs/domains/auth.md` 참조.
