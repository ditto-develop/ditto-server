# ADR 0026 — 매칭이 다루는 퀴즈셋을 이번 운영 주로 고정

- 상태: Accepted
- 근거: Issue #175 (FE 요청서 [BE-Request-Weekly-Matching-Type](https://github.com/ditto-develop/ditto-fe/wiki/BE-Request-Weekly-Matching-Type), 2026-09-14)

## Context

후보 조회·성사 전 열람 권한의 기준 퀴즈셋은 `findLatestCompletedQuizSet(memberId, matchingType)` — **타입별로 가장 최근에 완주한 셋**이었다. 주차를 보지 않는다.

후보 행은 지난 주 것도 남는다(1:1 `match_candidate`, 그룹 `group_match`). 그래서 이번 주에 그 타입을 풀지 않은 회원에게는 **지난 사이클 후보가 200으로 내려간다.** 1:1과 그룹이 각각 독립적으로 최신 셋을 찾으므로, 두 트랙을 같이 호출하는 홈 화면에서는 한쪽이 이번 주, 다른 쪽이 지난 주인 응답이 섞인다.

FE 는 `quizSetId` 크기 비교로 어느 쪽이 이번 주인지 추측하고 있었고, 한쪽이 404 면 비교 대상이 없어 **지난 주 카드를 이번 주 결과로 그렸다.** 서버 정본 기준(`endDate DESC`)과 같은 값을 응답에서 받을 수 없었고, 매칭 기간에는 `findCurrentWeekActive` 가 빈 배열이라(`startDate <= now <= endDate` 인데 퀴즈셋 `endDate` 는 배치가 도는 목 05:00 이전이다) "이번 주 퀴즈셋이 무엇인지" 물어볼 곳도 없었다.

조회만의 문제가 아니었다. **응답 경로에도 주차 가드가 없었다** — `acceptGroupMatch` 는 `groupMatchId` 만 받으므로 지난 주 후보 그룹을 오늘 수락하면 성사되고 채팅방이 열린다. 화면에서 감추는 것으로는 막히지 않는다(다른 탭·기기·직접 호출).

## Decision

매칭이 다루는 퀴즈셋을 **이번 운영 주**(`OperationWeek`, 월~일)로 고정한다. 판정은 `MatchWeekPolicy` 한 곳에 둔다.

- 기준 조회를 `findCompletedQuizSetInWeek(memberId, matchingType, weekStartedOn)` 로 바꾸고 주차 무관 조회는 **없앤다.** 남겨 두면 다음 사용처가 같은 함정을 밟는다.
- 후보 조회(`/matches/1on1`·`/matches/group`)는 이번 주에 그 타입을 완주하지 않았으면 `0004`. 지난 주 후보는 내려가지 않는다.
- 성사 전 열람 권한(`MatchAccessChecker.isMatchCandidate`)도 같은 기준을 쓴다 — 어긋나면 "후보 카드는 보이는데 소개노트는 403"이 된다.
- 응답 경로(그룹 수락·거절, 1:1 요청·수락·거절)는 대상 퀴즈셋이 이번 주가 아니면 `5008`(`NOT_MATCHING_PERIOD`).
- 후보 응답에 주차를 함께 싣는다 — 식별자 `weekStartedOn` + 파생 표시값 `year`/`month`/`week`([ADR 0010](0010-week-identifier-week-started-on.md) 규약, `SystemStateResponse` 와 같은 모양). 서버가 닫더라도 FE 가 무엇을 받았는지 화면·로그에서 확인할 수 있어야 한다.

### 버린 대안

- **값만 내려주고 FE 가 판정** (요청서 1안). 응답 경로 구멍이 그대로 남는다. 지난 주 그룹이 오늘 성사되는 것은 화면 분기로 막을 수 없다.
- **`isCurrentWeek` 불리언만 내려주기.** 판정을 서버가 쥐는 건 맞지만 FE 가 "어느 주 것인지"를 표시·디버깅할 수 없다.
- **`endDate` 내려주기.** FE 가 서버 정렬 기준을 복제하게 되고, 주차 비교를 시각 비교로 바꾸는 셈이라 경계에서 더 헷갈린다.

## Consequences

- 지난 사이클 후보가 카드·소개노트·응답 어디에도 새지 않는다. 판정이 한 곳이라 세 경로가 어긋날 수 없다.
- 그룹 수락 마감이 사실상 **일요일 자정**이 된다(월요일 00:00 에 지난 주 후보가 닫힌다). 기획의 "일요일 23:59 응답 마감"과 맞아떨어진다 — [#171](https://github.com/ditto-develop/ditto-server/issues/171)에서 채팅 창과 맞지 않아 적용하지 않았던 조항이 주차 고정으로 자연히 생긴다.
- 주 경계에서 매칭·채팅 기간이 잘리지 않는다. 후보 생성(목 05:00)도 채팅 개방(금 00:00)도 퀴즈 마감과 같은 운영 주 안이다. 이미 열린 채팅방은 `chat_room` 이 따로 들고 있어 후보가 닫혀도 영향받지 않는다.
- 이번 주에 퀴즈를 건너뛴 회원은 후보 화면에서 `0004` 를 받는다 — 이는 의도된 동작이다(참여하지 않은 주에는 결과가 없다).
- 한 회원이 한 주에 1:1·그룹을 모두 풀면 두 트랙이 모두 이번 주 후보를 돌려준다. 두 트랙이 독립이므로 주차 고정만으로는 "한 주에 채팅방 하나"가 보장되지 않는다 — 그 전제를 유지할지는 별도 결정이다.

## Links

- [Issue #175](https://github.com/ditto-develop/ditto-server/issues/175)
- FE 요청서: [BE-Request-Weekly-Matching-Type](https://github.com/ditto-develop/ditto-fe/wiki/BE-Request-Weekly-Matching-Type)
- `api/src/main/kotlin/com/ditto/api/match/MatchWeekPolicy.kt`
- 주간 식별자 규약: [ADR 0010](0010-week-identifier-week-started-on.md)
- 성사 전 열람: [ADR 0025](0025-intro-note-candidate-preview.md)
