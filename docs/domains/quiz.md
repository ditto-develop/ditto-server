# quiz 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

퀴즈·퀴즈셋·진행·답변. **골격 문서** — 불변식·상태전이는 퀴즈 작업 시 코드 확인 후 채운다.

## 용어
`Quiz`(질문), `QuizSet`(묶음), `QuizChoice`(선택지), `QuizAnswer`(답변), `QuizProgress`(진행), `MatchingType`(매칭 타입).

## 불변식
- 1차 제재(경고) 차단 구간에는 답안 제출·진행 초기화 불가 (`QUIZ_BLOCKED_BY_SANCTION`) — 구간은 `sanction`(WARNING·ACTIVE)의 starts/ends datetime, 판정 시각은 컨트롤러가 주입하는 `ServerTimeProvider.now()`. 배경: `docs/domains/sanction.md`.
- `QuizSet.weekStartedOn`(주간 식별자)은 항상 `startDate`가 속한 주의 월요일로 파생된다 — `create()`뿐 아니라 `update()`로 `startDate`가 다른 주로 바뀌면 함께 재파생된다. 유일 제약 없음(한 주 복수 퀴즈셋 허용). `year/month/week`는 저장하지 않고 `OperationWeek` 파생 표시값으로만 제공. 배경: [ADR 0010](../adr/0010-week-identifier-week-started-on.md).
- 어드민이 입력하는 퀴즈셋 기간(startDate~endDate)은 한 운영 주(월~일) 안에 있어야 한다 — 두 주에 걸치면 주간 식별자와 실제 기간이 어긋나므로 유입 지점(`AdminQuizService`)에서 거부. 엔티티 레벨 강제가 아닌 이유: 테스트 픽스처는 조회 로직 검증을 위해 임의 기간을 자유롭게 쓴다.
- 두 회원의 답변 일치 비교(프로필의 "나와 같은 답")는 **둘 다 완주(COMPLETED)한 가장 최근 퀴즈셋**을 기준으로 한다 — 완주해야 문항 수가 같아 비교가 성립한다. 조회는 `QuizProgressRepository.findLatestQuizSetIdCompletedByBoth`, 수치는 매칭과 같은 `MatchScoreCalculator`를 쓴다. 상대의 선택지는 노출하지 않는다: [ADR 0020](../adr/0020-peer-profile-answer-match-summary.md).
- 매칭이 기준으로 삼는 퀴즈셋은 `findCompletedQuizSetInWeek(memberId, matchingType, weekStartedOn)` — **회원이 그 운영 주에 완주한 해당 타입 셋**이다. 주차 없이 "가장 최근 완주"를 찾으면 지난 사이클 후보가 계속 노출된다([ADR 0026](../adr/0026-matching-scoped-to-operation-week.md)). 한 주에 같은 타입은 하나라는 전제로 단건을 돌려준다.
- 어드민의 문항 편집은 퀴즈셋 폼 한 번의 제출로 반영된다. `displayOrder`는 폼에서 받지 않고 `quizzes[i]`의 `i+1`로 서버가 넣으며, 선택지는 좌 1 · 우 2로 고정한다(문항당 2개). 각 행의 `id` hidden으로 update/insert/delete를 가른다 — 제출에 없는 기존 id가 삭제 대상이다. 배경: [ADR 0024](../adr/0024-admin-quiz-bulk-edit-implicit-order.md).
- 답변이 등록된 문항은 삭제할 수 없고 선택지 수도 바꿀 수 없다(문구 수정만 허용). `quiz_answer`가 `quiz_id`·`choice_id`를 FK 없이 참조해 지워도 DB가 막지 않고, `MatchScoreCalculator`가 그 짝의 일치로 점수를 내므로 답변이 매칭에서 조용히 빠진다.
- 폼의 `quizzes`가 비어 있으면 문항을 건드리지 않는다 — 문항 전체 삭제는 퀴즈셋 삭제로만 한다. 제출된 행은 모두 채워져 있어야 하며(빈 행도 거부) 삭제는 `문항 삭제` 버튼으로 id를 아예 안 보내는 한 경로뿐이다. 같은 문항 id 중복 제출도 거부한다.
- 참여가 시작된 퀴즈셋(`quiz_progress` 행 존재)은 문항 **개수**를 바꿀 수 없다 — `QuizProgress.totalCount`가 첫 답변 시점의 문항 수로 굳는 `val`이라, 문항이 줄면 그 회원이 `answeredCount >= totalCount`에 닿지 못해 완주할 수 없고 그 주 매칭에서 빠진다. 문구 수정은 허용.
- TODO: 퀴즈셋 구성·중복 응답 방지·진행 완료 조건을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `quiz/entity/QuizProgressStatus`.
- TODO: 진행 상태 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/quiz/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/quiz/repository/` (+ `querydsl/`)
