# quiz 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

퀴즈·퀴즈셋·진행·답변. **골격 문서** — 불변식·상태전이는 퀴즈 작업 시 코드 확인 후 채운다.

## 용어
`Quiz`(질문), `QuizSet`(묶음), `QuizChoice`(선택지), `QuizAnswer`(답변), `QuizProgress`(진행), `MatchingType`(매칭 타입).

## 불변식
- 1차 제재(경고) 차단 구간에는 답안 제출·진행 초기화 불가 (`QUIZ_BLOCKED_BY_SANCTION`) — 구간은 `sanction`(WARNING·ACTIVE)의 starts/ends datetime, 판정 시각은 컨트롤러가 주입하는 `ServerTimeProvider.now()`. 배경: `docs/domains/sanction.md`.
- TODO: 퀴즈셋 구성·중복 응답 방지·진행 완료 조건을 코드 확인 후 기술.

## 상태 전이
- 상태 enum: `quiz/entity/QuizProgressStatus`.
- TODO: 진행 상태 전이를 서비스 로직 확인 후 명시.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/quiz/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/quiz/repository/` (+ `querydsl/`)
