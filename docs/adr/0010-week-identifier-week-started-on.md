# ADR 0010 — 주간 식별자 SSOT를 weekStartedOn(그 주 월요일 날짜)으로 정규화

- 상태: Accepted
- 근거: issue #106

## Context

서비스는 월~일 주간 사이클(월~수 퀴즈 / 목 매칭 / 금~일 채팅, `SystemPeriod`)로 돌지만, 주간 식별은 `QuizSet`의 `year/month/week` 저장값과 `WeekFields.of(MONDAY, 1).weekOfMonth()` 당일 계산이 3곳(`QuizSetService`·`SystemStateProvider`·`WeekLabel`)에 흩어져 있었다. `weekOfMonth`는 "달 안에서 몇 번째 주"라 월 경계에 걸친 같은 월~일 주(예: 2026-07-27~08-02)가 조회 시점에 따라 "7월 5주차"와 "8월 1주차"로 갈라진다. 그 결과 한 응답 안에서 최상위/항목 주차가 불일치할 수 있고, 어드민 정렬·라벨이 실제 주간 순서와 어긋나며, 리뷰 재매칭의 "같은 회원 쌍 주당 1회" 정책이 쓸 안정적인 주간 키가 없었다.

대안: (a) `quizSetId`를 주간 키로 — 한 주에 퀴즈셋이 여러 개면 정책이 분리된다. (b) ISO 주번호(연+주차 번호) — 연 경계에서 ISO 연도와 달력 연도가 어긋나는 새 혼동을 만든다. (c) 그 주 월요일의 `LocalDate` — 같은 월~일이면 항상 같은 값, 정렬·비교 가능, 해석 여지 없음. (c)를 채택.

## Decision

`OperationWeek` 값 객체(`domain/system`)를 주차 계산의 단일 책임으로 둔다 — `containing(date)`가 어떤 날짜든 그 주 월요일로 스냅하고, 월요일 불변식은 `init require`로 강제하며, `year/month/weekOfMonth`는 표시용 파생값으로만 제공한다.

`quiz_set.week_started_on DATE`를 저장한다. 값은 항상 `startDate`에서 파생된다 — `QuizSet.create()`(생성자 private)에서 파생하고, `update()`로 `startDate`가 다른 주로 바뀌면 재파생한다(별도 입력값이던 옛 `year/month/week`와 달리 파생값이므로 보존할 독립 정보가 없다). 유일 제약은 두지 않아 한 주 복수 퀴즈셋을 허용한다. 기간이 두 운영 주에 걸치면 주간 식별자와 실제 기간이 어긋나므로 어드민 유입 지점(`AdminQuizService`)에서 거부한다 — 엔티티 레벨로 강제하지 않는 이유는 테스트 픽스처가 조회 로직 검증용 임의 기간을 자유롭게 쓰기 때문.

기존 `year_no/month_no/week_no` 컬럼·엔티티 필드는 제거했다(정식 오픈 전이라 2단계 배포 부담을 수용). API 응답의 `year/month/week` 필드는 FE가 표시에 사용 중이므로 `weekStartedOn` 파생 표시값으로 유지하고, FE 전환 후 별도 PR에서 제거한다(REST Docs에 명시).

향후 리뷰 재매칭의 주간 제한 키는 `UNIQUE(week_started_on, member_id_1, member_id_2)`를 사용한다(쌍 정규화는 ADR 0008 패턴 재사용). `quizSetId`는 원본 추적용으로만 유지한다.

## Consequences

- 얻음: 월·연 경계와 무관한 안정적 주간 키, 주차 계산 단일화(중복 3곳 제거), 한 주 복수 퀴즈셋 허용, 재매칭 주간 정책 키 확보, 어드민 주차 수동 입력 제거(시작일에서 자동 파생).
- 비용: `year/month/week` API 필드가 FE 전환까지 파생값으로 이중 표현 유지, 마이그레이션 2단계 적용 필요 — 추가(`V20260726202324`, NULL 허용+백필)는 언제든 안전하고, NOT NULL 승격+제거(`V20260726204352`)는 신버전 배포 완료 후에만 적용(구버전은 제거 컬럼을 조회·기록하고 새 컬럼을 채우지 않음).

## Links

- issue #106
- `domain/src/main/kotlin/com/ditto/domain/system/OperationWeek.kt`
- `domain/db/V20260726202324_퀴즈셋 주 시작일 컬럼 추가.sql`, `domain/db/V20260726204352_퀴즈셋 연월주차 컬럼 제거.sql`
- [ADR 0008 — 매칭 엔티티 유니크 모델링](0008-matching-entity-uniqueness-modeling.md)
