# ADR 0029 — 타인 프로필·평점 열람을 성사 전 후보까지 열되, 후보에게는 요약만 준다

- 상태: Accepted (2026-09-17)
- 근거: 프로덕션 로그 진단(3일간 `GET /users/{id}/ratings` 39콜 39건 403, 성공 0건) + [ADR 0025](0025-intro-note-candidate-preview.md)의 후보 공개 원칙

## Context

[ADR 0020](0020-peer-profile-answer-match-summary.md)은 타인 프로필 본문·평점·답변 비교가 하나의 판정
(`UserService.checkProfileAccess`)을 공유하게 했고, 그 판정은 `MatchAccessChecker.isMatched`
— **성사 후** 관계만 통과시켰다.

그 뒤 [ADR 0025](0025-intro-note-candidate-preview.md)가 소개노트에 한해 **성사 전 후보** 구간을 열었다
(`isMatchCandidate`, 미리보기 3문항). 그 결과 같은 화면("대화 신청 여부를 정하는 화면", 피그마 3.2)에서
소개노트는 열리는데 프로필 본문·평점·답변 비교는 403이 되는 비대칭이 생겼다.

프로덕션 로그가 그 비대칭을 그대로 보여줬다. 2026-09-14~17 사흘간 `GET /users/{id}/ratings` 는
**39콜 전부 403**이었고(성공 0건), 호출한 13쌍은 모두 후보 관계였다. FE는 실패를 삼켜
(`getUserRatingSummary(...).catch(() => null)`) 평가 섹션만 비운 채 화면을 그리고 있었다 —
사용자에게는 "평가가 없는 사람"으로 보였다.

## Decision

`checkProfileAccess`는 Unit 대신 **공개 등급**(`ProfileAccessLevel`)을 반환하고, 세 갈래로 판정한다.

| 관계 | 등급 | 평점 응답 |
|---|---|---|
| 본인 · 매칭 성사(ACCEPTED) · 같은 그룹 채팅 참여 | `FULL` | 평균·건수 + 코멘트 + 노쇼 횟수 |
| 이번 주 매칭 후보 = 성사 전 (`isMatchCandidate`) | `SUMMARY` | 평균·건수만 (`noShowCount=null`, `ratings=[]`) |
| 그 외 | — | `FORBIDDEN` |

- **차단 관계는 등급을 가리지 않고 막는다.** 후보 행은 계산 시점 스냅샷이라 그 뒤 생긴 차단이 반영되지
  않으므로, 성사 이력이 있든 후보든 `memberBlockRepository.existsBetween`을 먼저 본다
  ("차단한 사용자는 나의 프로필을 볼 수 없고", 피그마 6.2.2).
- **후보에게 코멘트와 노쇼 횟수를 감추는 이유**는 소개노트를 3문항으로 줄인 이유와 같다 — 성사 전 구간은
  "신청 여부를 정할 만큼"만 연다. 제3자가 남긴 코멘트는 성사 전 관계에 넘길 값이 아니다.
- `noShowCount`는 0이 아니라 **null**로 비운다. 0으로 내리면 "노쇼 0회"라고 단언하는 셈이라,
  실제 0건과 비공개를 구분할 수 없다.
- **답변 일치(`/users/{id}/answers`)는 등급을 가르지 않는다.** 후보 목록
  (`MatchCandidateResponse.matchRate`)이 이미 같은 수치를 내리고 있어 새로 열리는 정보가 없고,
  상대의 선택지는 어느 등급에서도 나가지 않는다([ADR 0020](0020-peer-profile-answer-match-summary.md)).
- **공개 프로필 본문(`/users/{id}/profile`)도 두 등급이 같다.** 후보 카드에 이미 실려 있는 값이고,
  상단 평균 별점은 `findPublicAverageScore`가 공개 기준(3건)으로 한 번 더 거른다.

## Consequences

- 얻음: 후보 프로필 화면의 평가 카드가 처음으로 값을 받는다. 소개노트·프로필·평점이 같은 관계 모델을 쓴다.
- 비용: `MyRatingsResponse.noShowCount`가 nullable이 된다. `me`와 성사 후 응답은 그대로라 기존 화면은
  영향이 없지만, **FE 타입(`RatingSummary.noShowCount`)을 `number | null`로 넓혀야 한다.**
- 감수한 것: 성사 전 상대에게 **평균 별점과 평가 건수가 노출된다.** 아직 아무 관계도 아닌 상대가 볼 수 있다는
  뜻이다. 평균 별점 자체는 이미 `PublicProfileResponse.rating`으로 후보에게 나가고 있었고
  (같은 3건 공개 기준), 평가가 신뢰 지표로 쓰이는 제품이라 요약 공개를 택했다.
  되돌리려면 `SUMMARY` 분기를 `FORBIDDEN`으로 바꾸면 된다.

## Links

- 상위 결정: [ADR 0020](0020-peer-profile-answer-match-summary.md)(하나의 판정 공유), [ADR 0025](0025-intro-note-candidate-preview.md)(후보 구간 공개 원칙), [ADR 0026](0026-matching-scoped-to-operation-week.md)(기준 퀴즈셋 = 이번 운영 주)
- 핵심 파일: `api/.../user/service/ProfileAccessLevel.kt`, `api/.../user/service/UserService.kt`(판정), `api/.../user/service/PeerProfileService.kt`(등급별 분기), `api/.../user/service/MemberRatingService.kt`(요약본)
