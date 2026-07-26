# quiz 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

퀴즈·퀴즈셋·진행·답변. **골격 문서** — 불변식·상태전이는 퀴즈 작업 시 코드 확인 후 채운다.

## 용어
`Quiz`(질문), `QuizSet`(묶음), `QuizChoice`(선택지), `QuizAnswer`(답변), `QuizProgress`(진행), `MatchingType`(매칭 타입).

## 불변식
- 1차 제재(경고) 차단 구간에는 답안 제출·진행 초기화 불가 (`QUIZ_BLOCKED_BY_SANCTION`) — 구간은 `sanction`(WARNING·ACTIVE)의 starts/ends datetime, 판정 시각은 컨트롤러가 주입하는 `ServerTimeProvider.now()`. 배경: `docs/domains/sanction.md`.
- `QuizSet.weekStartedOn`(주간 식별자)은 항상 `startDate`가 속한 주의 월요일로 파생된다 — `create()`뿐 아니라 `update()`로 `startDate`가 다른 주로 바뀌면 함께 재파생된다. 유일 제약 없음(한 주 복수 퀴즈셋 허용). `year/month/week`는 저장하지 않고 `OperationWeek` 파생 표시값으로만 제공. 배경: [ADR 0010](../adr/0010-week-identifier-week-started-on.md).
- 어드민이 입력하는 퀴즈셋 기간(startDate~endDate)은 한 운영 주(월~일) 안에 있어야 한다 — 두 주에 걸치면 주간 식별자와 실제 기간이 어긋나므로 유입 지점(`AdminQuizService`)에서 거부. 엔티티 레벨 강제가 아닌 이유: 테스트 픽스처는 조회 로직 검증을 위해 임의 기간을 자유롭게 쓴다.
- TODO: 퀴즈셋 구성·중복 응답 방지·진행 완료 조건을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `quiz/entity/QuizProgressStatus`.
- TODO: 진행 상태 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/quiz/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/quiz/repository/` (+ `querydsl/`)
