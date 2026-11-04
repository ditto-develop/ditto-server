# system 도메인

서버 시각 오버라이드(어드민이 "현재 시각"을 임의 지정)와 서버 기능 플래그(어드민이 기능별로 켜고 끔). **골격 문서** — 불변식·상태전이는 시스템 설정 작업 시 코드 확인 후 채운다.

## 용어
`ServerTimeOverride`(오버라이드 활성 여부·지정 시각·최종 설정자 이름/이메일), `OperationWeek`(운영 주 값 객체 — 그 주 월요일 날짜가 주간 식별자, [ADR 0010](../adr/0010-week-identifier-week-started-on.md)), `ShedLock`(크론 중복 실행 방지 락 테이블 — 행은 ShedLock 라이브러리가 JDBC 로 직접 다루고 엔티티는 스키마 검증용, [ADR 0049](../adr/0049-scheduler-lock-shedlock.md)).

## 불변식
- 단일 행 운영 — `findFirstByOrderByIdAsc`로 가장 먼저 생성된 행 1개만 사용.
- 적용 시각은 `resolve(fallback)`: `enabled`이고 `overrideDateTime`이 있으면 그 값, 아니면 `fallback`(실제 시각).
- QA 도구 스위치(`ADMIN_QA_TOOLS_ENABLED`)가 꺼져 있으면 `ServerTimeProvider`는 행을 읽지 않고 실제 시각을 쓴다. 꺼진 채 서버가 뜨면 `LeftoverTimeOverrideDisabler`가 행을 `disable()`해, 스위치를 켜도 예전 시각이 살아나지 않는다. [ADR 0039](../adr/0039-admin-qa-tools-env-switch.md)
- `disable()`은 `enabled`만 끄고 설정 이력(`overrideDateTime`/설정자)은 남긴다.
- 주차 계산은 `OperationWeek.containing(date)` 단일 경로 — 월요일이 아닌 날짜로 `OperationWeek`를 직접 만들 수 없고(`require`), `SystemState`의 `year/month/week`는 현재 날짜가 아닌 주 시작 월요일 기준 파생 표시값이다.

## 기능 플래그 ([ADR 0051](../adr/0051-server-feature-flags.md))
- 플래그는 `FeatureFlagKey` enum, 상태는 `feature_flag` 플래그당 한 행. **행이 없으면 꺼짐.**
- 읽기(`FeatureFlags`)는 인스턴스마다 1분 캐시. 어드민이 바꾼 서버는 커밋 뒤 곧바로, 다른 서버는 최대 1분 뒤 반영된다.
- `enabled_at`은 마지막으로 켠 시각(서버 시각, 화면 표시용). 이미 켜진 플래그를 다시 켜도 유지, 끄면 지운다.
- 켜기 전부터 진행 중이던 대상은 시각으로 거르지 않고, 꺼짐 → 켜짐 순간 `FeatureFlagEnableListener`가 같은 트랜잭션에서 빼 둔다. 전환은 플래그 행을 잠그고 한다.
- 어드민 화면 `/admin/feature-flags`는 QA 도구 스위치와 무관하게 열린다.

## 상태 전이
- `override(dateTime, authorName, authorEmail)` → `enabled = true`.
- `disable()` → `enabled = false` (이후 실제 시각 사용).

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/system/entity/ServerTimeOverride.kt`, `ShedLock.kt`, `FeatureFlag.kt`(+`FeatureFlagKey`)
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/system/repository/ServerTimeOverrideRepository.kt`
- API: `api/src/main/kotlin/com/ditto/api/system/`(+`featureflag/`), `api/src/main/kotlin/com/ditto/api/admin/system/`, `api/src/main/kotlin/com/ditto/api/admin/featureflag/`
