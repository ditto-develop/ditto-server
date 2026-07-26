# system 도메인

서버 시각 오버라이드(어드민이 "현재 시각"을 임의 지정). **골격 문서** — 불변식·상태전이는 시스템 설정 작업 시 코드 확인 후 채운다.

## 용어
`ServerTimeOverride`(오버라이드 활성 여부·지정 시각·최종 설정자 이름/이메일), `OperationWeek`(운영 주 값 객체 — 그 주 월요일 날짜가 주간 식별자, [ADR 0010](../adr/0010-week-identifier-week-started-on.md)).

## 불변식
- 단일 행 운영 — `findFirstByOrderByIdAsc`로 가장 먼저 생성된 행 1개만 사용.
- 적용 시각은 `resolve(fallback)`: `enabled`이고 `overrideDateTime`이 있으면 그 값, 아니면 `fallback`(실제 시각).
- `disable()`은 `enabled`만 끄고 설정 이력(`overrideDateTime`/설정자)은 남긴다.
- 주차 계산은 `OperationWeek.containing(date)` 단일 경로 — 월요일이 아닌 날짜로 `OperationWeek`를 직접 만들 수 없고(`require`), `SystemState`의 `year/month/week`는 현재 날짜가 아닌 주 시작 월요일 기준 파생 표시값이다.

## 상태 전이
- `override(dateTime, authorName, authorEmail)` → `enabled = true`.
- `disable()` → `enabled = false` (이후 실제 시각 사용).

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/system/entity/ServerTimeOverride.kt`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/system/repository/ServerTimeOverrideRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/system/`, `api/src/main/kotlin/com/ditto/api/admin/system/`
