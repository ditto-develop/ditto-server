# quiz 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

퀴즈·퀴즈셋·진행·답변. **골격 문서** — 불변식·상태전이는 퀴즈 작업 시 코드 확인 후 채운다.

## 용어
`Quiz`(질문), `QuizSet`(묶음), `QuizChoice`(선택지), `QuizAnswer`(답변), `QuizProgress`(진행), `MatchingType`(매칭 타입).

## 불변식
- 1차 제재(경고) 차단 구간에는 답안 제출·진행 초기화 불가 (`QUIZ_BLOCKED_BY_SANCTION`) — 구간은 `sanction`(WARNING·ACTIVE)의 starts/ends datetime, 판정 시각은 컨트롤러가 주입하는 `ServerTimeProvider.now()`. 배경: `docs/domains/sanction.md`.
- `QuizSet.weekStartedOn`(주간 식별자)은 항상 `startDate`가 속한 주의 월요일로 파생된다 — `create()`뿐 아니라 `update()`로 `startDate`가 다른 주로 바뀌면 함께 재파생된다. 유일 제약 없음(한 주 복수 퀴즈셋 허용). `year/month/week`는 저장하지 않고 `OperationWeek` 파생 표시값으로만 제공. 배경: [ADR 0010](../adr/0010-week-identifier-week-started-on.md).
- 어드민은 퀴즈셋 기간을 직접 입력하지 않는다. 주차(그 주 월요일, `QuizSetForm.weekStartedOn`)만 고르면 `QuizResponsePeriod`가 **월요일 00:00:00 ~ 수요일 23:59:59**로 기간을 만든다 — 목 05:00 매칭 배치(`endDate < now`)·금 채팅 개시 흐름에 맞춘 값이고, 일시 자유 입력 시절의 실수(한 주 이탈·요일 어긋남)를 없애기 위해서다. 종료를 `LocalTime.MAX`로 두지 않는 이유: DATETIME(6) 저장 시 반올림돼 목요일 00:00이 될 수 있다. 자유 입력 시절에 만든 셋도 수정 저장하면 같은 규칙으로 기간이 다시 쓰인다. 엔티티(`QuizSet.create`)는 여전히 임의 기간을 받는다 — 테스트 픽스처가 조회 로직 검증에 임의 기간을 쓴다. 화면은 flatpickr 달력에서 월요일만 활성화하고, 스크립트가 없으면 date 입력 + 월요일 스냅으로 내려간다(`quiz-week-picker.js`).
- 두 회원의 답변 일치 비교(프로필의 "나와 같은 답")는 **둘 다 완주(COMPLETED)한 가장 최근 퀴즈셋**을 기준으로 한다 — 완주해야 문항 수가 같아 비교가 성립한다. 조회는 `QuizProgressRepository.findLatestQuizSetIdCompletedByBoth`, 수치는 매칭과 같은 `MatchScoreCalculator`를 쓴다. 상대의 선택지는 노출하지 않는다: [ADR 0020](../adr/0020-peer-profile-answer-match-summary.md).
- 어드민의 문항 편집은 퀴즈셋 폼 한 번의 제출로 반영된다. `displayOrder`는 폼에서 받지 않고 `quizzes[i]`의 `i+1`로 서버가 넣으며, 선택지는 좌 1 · 우 2로 고정한다(문항당 2개). 각 행의 `id` hidden으로 update/insert/delete를 가른다 — 제출에 없는 기존 id가 삭제 대상이다. 배경: [ADR 0024](../adr/0024-admin-quiz-bulk-edit-implicit-order.md).
- 답변이 등록된 문항은 삭제할 수 없고 선택지 수도 바꿀 수 없다(문구 수정만 허용). `quiz_answer`가 `quiz_id`·`choice_id`를 FK 없이 참조해 지워도 DB가 막지 않고, `MatchScoreCalculator`가 그 짝의 일치로 점수를 내므로 답변이 매칭에서 조용히 빠진다.
- 폼의 `quizzes`가 비어 있으면 문항을 건드리지 않는다 — 문항 전체 삭제는 퀴즈셋 삭제로만 한다. 제출된 행은 모두 채워져 있어야 하며(빈 행도 거부) 삭제는 `문항 삭제` 버튼으로 id를 아예 안 보내는 한 경로뿐이다. 같은 문항 id 중복 제출도 거부한다.
- 참여가 시작된 퀴즈셋(`quiz_progress` 행 존재)은 문항 **개수**를 바꿀 수 없다 — `QuizProgress.totalCount`가 첫 답변 시점의 문항 수로 굳는 `val`이라, 문항이 줄면 그 회원이 `answeredCount >= totalCount`에 닿지 못해 완주할 수 없고 그 주 매칭에서 빠진다. 문구 수정은 허용.
- `GET /api/v1/quiz-progress/current`는 활성 퀴즈셋이 없어도 오류를 내지 않고 `NOT_STARTED`·`participantCount=0`을 준다. 응답 기간이 월~수로 고정되면서 목~일은 항상 이 상태이고, 이번 주 퀴즈셋을 아직 만들지 않은 월~수도 같다 — 정상 운영을 5xx(ERROR 로그)로 다루지 않는다. FE 홈은 `period=QUIZ`일 때만 이 API를 부르며, 실패든 빈 응답이든 같은 화면(미완료·참여자 0)을 그린다.
- TODO: 퀴즈셋 구성·중복 응답 방지·진행 완료 조건을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `quiz/entity/QuizProgressStatus`.
- TODO: 진행 상태 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/quiz/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/quiz/repository/` (+ `querydsl/`)
