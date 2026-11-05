# quiz 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

퀴즈·퀴즈셋·진행·답변. **골격 문서** — 불변식·상태전이는 퀴즈 작업 시 코드 확인 후 채운다.

## 용어
`Quiz`(질문), `QuizSet`(묶음), `QuizChoice`(선택지), `QuizAnswer`(답변), `QuizProgress`(진행), `MatchingType`(매칭 타입).

## 불변식
- 1차 제재(경고) 차단 구간에는 답안 제출·진행 초기화 불가 (`QUIZ_BLOCKED_BY_SANCTION`) — 구간은 `sanction`(WARNING·ACTIVE)의 starts/ends datetime, 판정 시각은 컨트롤러가 주입하는 `ServerTimeProvider.now()`. 배경: `docs/domains/sanction.md`.
- `QuizSet.weekStartedOn`(주간 식별자)은 항상 `startDate`가 속한 주의 월요일로 파생된다 — `create()`뿐 아니라 `update()`로 `startDate`가 다른 주로 바뀌면 함께 재파생된다. DB 유일 제약은 없지만 **활성 셋은 주차·타입당 하나**로 서비스가 강제한다(아래). `year/month/week`는 저장하지 않고 `OperationWeek` 파생 표시값으로만 제공. 배경: [ADR 0010](../adr/0010-week-identifier-week-started-on.md).
- 어드민은 퀴즈셋 기간을 직접 입력하지 않는다. 주차(그 주 월요일, `QuizSetForm.weekStartedOn`)만 고르면 `QuizResponsePeriod`가 **월요일 00:00:00 ~ 수요일 23:59:59**로 기간을 만든다 — 목 00:01 매칭 배치(`endDate < now`)·금 채팅 개시 흐름에 맞춘 값이고, 일시 자유 입력 시절의 실수(한 주 이탈·요일 어긋남)를 없애기 위해서다. 종료를 `LocalTime.MAX`로 두지 않는 이유: DATETIME(6) 저장 시 반올림돼 목요일 00:00이 될 수 있다. 자유 입력 시절에 만든 셋도 수정 저장하면 같은 규칙으로 기간이 다시 쓰인다. 엔티티(`QuizSet.create`)는 여전히 임의 기간을 받는다 — 테스트 픽스처가 조회 로직 검증에 임의 기간을 쓴다. 화면은 flatpickr 달력에서 월요일만 활성화하고, 스크립트가 없으면 date 입력 + 월요일 스냅으로 내려간다(`quiz-week-picker.js`).
- 두 회원의 답변 일치 비교(프로필의 "나와 같은 답")는 **둘 다 완주(COMPLETED)한 가장 최근 퀴즈셋**을 기준으로 한다 — 완주해야 문항 수가 같아 비교가 성립한다. 조회는 `QuizProgressRepository.findLatestQuizSetIdCompletedByBoth`, 수치는 매칭과 같은 `MatchScoreCalculator`를 쓴다. 상대의 선택지는 노출하지 않는다: [ADR 0020](../adr/0020-peer-profile-answer-match-summary.md).
- 매칭이 기준으로 삼는 퀴즈셋은 `findCompletedQuizSetInWeek(memberId, matchingType, weekStartedOn)` — **회원이 그 운영 주에 완주한 해당 타입의 활성 셋**이다. 주차 없이 "가장 최근 완주"를 찾으면 지난 사이클 후보가 계속 노출된다([ADR 0026](../adr/0026-matching-scoped-to-operation-week.md)).
- **활성 셋은 주차·타입당 하나다.** 생성·수정·활성화 세 경로가 모두 `AdminQuizService.validateNoOtherActiveInWeek`를 타고, 이미 활성인 셋이 있으면 거부한다 — 기존 셋을 자동으로 끄지 않는 이유는 운영 중인 셋이 말없이 닫히면 안 되기 때문이다. 타입까지 범위에 넣는 것은 한 주에 1:1용과 그룹용을 나란히 여는 것이 정상 운영이라서다(`findCurrentWeekActive`가 여러 셋을 돌려준다).
  - 이 강제가 없던 동안 같은 주차·타입에 활성 셋이 둘 생겨 매칭이 엉뚱한 셋을 가리켰다. `findCompletedQuizSetInWeek`가 활성 여부를 보지 않았고, 정렬도 `endDate desc` 하나뿐이라 순서가 정해지지 않았다 — `QuizResponsePeriod`가 기간을 월~수로 고정하므로 **같은 주 셋끼리는 `endDate`가 항상 같다.** 지금은 활성 조건과 `id desc` tie-break를 함께 둔다.
  - `is_active`의 뜻이 "응답 접수 중"에서 "그 주의 대표 셋"으로 넓어졌다. 매칭 기간(목~일)에 셋을 비활성화하면 그 주 후보 조회·수락·거절이 `NOT_FOUND`가 된다.
- 어드민의 문항 편집은 퀴즈셋 폼 한 번의 제출로 반영된다. `displayOrder`는 폼에서 받지 않고 `quizzes[i]`의 `i+1`로 서버가 넣으며, 선택지는 좌 1 · 우 2로 고정한다(문항당 2개). 각 행의 `id` hidden으로 update/insert/delete를 가른다 — 제출에 없는 기존 id가 삭제 대상이다. 배경: [ADR 0024](../adr/0024-admin-quiz-bulk-edit-implicit-order.md).
- 답변이 등록된 문항은 삭제할 수 없고 선택지 수도 바꿀 수 없다(문구 수정만 허용). `quiz_answer`가 `quiz_id`·`choice_id`를 FK 없이 참조해 지워도 DB가 막지 않고, `MatchScoreCalculator`가 그 짝의 일치로 점수를 내므로 답변이 매칭에서 조용히 빠진다.
- 폼의 `quizzes`가 비어 있으면 문항을 건드리지 않는다 — 문항 전체 삭제는 퀴즈셋 삭제로만 한다. 제출된 행은 모두 채워져 있어야 하며(빈 행도 거부) 삭제는 `문항 삭제` 버튼으로 id를 아예 안 보내는 한 경로뿐이다. 같은 문항 id 중복 제출도 거부한다.
- 참여가 시작된 퀴즈셋(`quiz_progress` 행 존재)은 문항 **개수**를 바꿀 수 없다 — `QuizProgress.totalCount`가 첫 답변 시점의 문항 수로 굳는 `val`이라, 문항이 줄면 그 회원이 `answeredCount >= totalCount`에 닿지 못해 완주할 수 없고 그 주 매칭에서 빠진다. 문구 수정은 허용.
- 매칭이 진행된 퀴즈셋(`match_candidate`·`personal_match`·`group_match` 중 하나라도 있음)은 삭제할 수 없다. 그 주 매칭·채팅·평가·재매칭이 `quiz_set_id`로 셋을 FK 없이 참조해, 지우면 그 주 데이터가 통째로 기준을 잃는다(평가 누락 복구가 그 방들을 매 주기 다시 집어 배치를 막은 적이 있다, #130). 평가·재매칭은 매칭에서만 생기므로 매칭 기록만 본다. 어드민 상세 화면은 이런 셋에 삭제 버튼 대신 안내를 띄운다.
  - 매칭 전 셋은 문항·선택지와 함께 회원 답변(`quiz_answer`)·진행(`quiz_progress`), 셋 행을 읽는 매칭 결과 알림(`MATCH_RESULT`·`NO_MATCH`)까지 지운다(`QuizSetDeleter`). 응답 기간 중에 지우면 그 주 참여자의 답변도 사라진다.
  - 퀴즈 열림·마감 알림(`QUIZ_OPENED`·`QUIZ_CLOSING_SOON`)은 남긴다. 그 주 활성 셋 중 id 최소값을 대표로 가리킬 뿐이라, 1:1·그룹 셋이 함께 열린 주에 한쪽을 지워도 알림은 여전히 맞다.
  - 매칭 여부 검사와 삭제 사이에 잠금이 없다. 목 00:01 매칭 배치가 커밋하기 직전에 지우면 지워진 셋으로 후보가 남을 수 있지만, 배치가 도는 짧은 순간에 어드민이 지워야만 생겨 감수한다.
  - QA 도구 스위치(`ADMIN_QA_TOOLS_ENABLED`)가 켜져 있으면 어드민이 셋의 매칭 기록을 지우고 다시 매칭하거나(매칭 기록 초기화), 셋까지 지울 수 있다(강제 삭제). 답·진행도 셋 전체(매칭 기록 포함)나, 매칭 전 셋이면 회원 한 명 단위로 지울 수 있다(답·진행 초기화). 두 초기화 모두 이번 운영 주 셋만 된다. 실회원끼리 열린 방·평가도 지워진다. 배경: [ADR 0039](../adr/0039-admin-qa-tools-env-switch.md).
- **한 주에는 1:1·그룹 중 한 유형만 푼다.** 같은 주 다른 활성 셋을 완주했으면 답 제출을 `QUIZ_OTHER_TYPE_ALREADY_COMPLETED`로 거부한다. 풀다 말았으면 막지 않고, 이 유형에 답하는 순간 풀던 유형의 답·진행을 지운다. 회원이 유형을 바꾸려고 reset 을 찾아야 한다는 걸 알기 어려워서다. 따라서 그 주 진행 기록은 하나뿐이라 `quiz-progress/current`는 둘 이상이면 오류로 본다. 이 검사가 없던 10/26 주에 둘 다 완주한 회원이 생겼다(#356).
  - 실수로 다른 유형에 답해도 풀던 답이 지워진다. 확인은 FE 몫이다.
  - 활성 셋만 보므로 응답 기간 중에 셋을 껐다 켜면 빠져나갈 수 있다. 그런 운영이 드물어 막지 않는다. 두 셋 첫 답을 동시에 보내는 경합도 잠금 없이 감수한다.
- `GET /api/v1/quiz-progress/current`는 활성 퀴즈셋이 없어도 오류를 내지 않고 `NOT_STARTED`·`participantCount=0`을 준다. 응답 기간이 월~수로 고정되면서 목~일은 항상 이 상태이고, 이번 주 퀴즈셋을 아직 만들지 않은 월~수도 같다 — 정상 운영을 5xx(ERROR 로그)로 다루지 않는다. FE 홈은 `period=QUIZ`일 때만 이 API를 부르며, 실패든 빈 응답이든 같은 화면(미완료·참여자 0)을 그린다.
- TODO: 퀴즈셋 구성·중복 응답 방지·진행 완료 조건을 코드 확인 후 기술.

## 퀴즈 기록·통계 (IMP-1)
- **"참여" = 그 셋을 끝까지 제출(COMPLETED)한 것이다.** 중간에 멈춘 셋은 기록·통계 모두에서 빠진다.
- **결과 공개 시각 = 응답 마감 뒤 첫 자정**(`QuizResultSchedule`, 지금 규칙이면 목 00:00). 그 전에는 비율·소수 의견·겹친 사람을 응답에 싣지 않는다(키 자체가 없다) — 화면이 안 그리는 것만으로는 값이 실려 나가 미리 볼 수 있다. 판단은 서버 시각이다.
- **마감 스냅샷**(`quiz_set_stat`·`quiz_choice_stat`·`quiz_member_stat`)이 공개 뒤 숫자의 원본이다. 목 00:00 배치(`QuizStatSnapshotScheduler`, 매칭 00:01 보다 앞)가 만들고, 배치가 놓쳤으면 조회하는 쪽이 그 자리에서 만든다. 셋당 1회, 동시 생성은 머리 행 유니크가 막고 진 쪽은 다시 읽는다. 마감 뒤 답은 바뀌지 않으므로 다시 계산하지 않는다.
  - 모집단은 마감 시점 완주자 전원이다. 어드민도 실제로 퀴즈·매칭에 참여한다. 빼면 진행 중(홈) 참여자 수와 공개 뒤 숫자가 어긋나고, 어드민 기록의 겹친 사람이 늘 0이 된다(#367). #367 전에 만든 스냅샷은 어드민이 빠져 있어 [통계 스냅샷 다시 만들기]로 다시 만든다.
  - 비율은 정수 %, 문항 안 합 100 이 되도록 최대 잉여법으로 보정해 **저장한다**. 화면마다 다시 계산하면 반올림이 갈린다.
  - **소수 의견** = `선택 수 × 5 ≤ 참여자 수` 이면서 참여자 20명 이상. 반올림한 %가 아니라 실제 수로 판정한다(19.6%와 20.4%가 같은 20이 되지 않게).
  - **겹친 사람** = 같은 셋 완주자 중 본인 제외, 같은 답이 `ceil(문항 수 × 2/3)`개 이상인 사람 수(12문항 8개). 기준은 스냅샷 시점 문항 수로 고정해 저장한다.
- 문항 문구는 최신 값, 숫자는 스냅샷 값이다.
- 진행 중 참여자 수는 홈(`GET /quiz-progress/current`)과 같은 값 — 이번 주 활성 셋 전체의 완주자 수다.
- 어드민 시각 오버라이드로 시각을 마감 뒤로 옮기면 그 순간까지의 답으로 스냅샷이 굳는다. dev 검증 데이터에만 생긴다. 퀴즈셋 상세의 [통계 스냅샷 다시 만들기]와 QA 답 초기화가 스냅샷을 지워 다음 조회가 다시 만든다.
- 문항별 **대화 힌트 질문**(`quiz.hint_question`, 선택, 최대 200자)은 어드민 퀴즈 폼에서 입력한다. 첫 대화 힌트·질문 카드·답변 비교에 실린다.
- API: `GET /api/v1/quiz-history`(기록 탭), `GET /api/v1/quiz-history/{quizSetId}`(주차 상세, 완주하지 않았으면 404 `QUIZ_HISTORY_NOT_FOUND`).

## 상태 전이
- 상태 enum: `quiz/entity/QuizProgressStatus`.
- TODO: 진행 상태 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/quiz/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/quiz/repository/` (+ `querydsl/`)
