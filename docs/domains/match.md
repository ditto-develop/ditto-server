# match 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

매칭(1:1 / 그룹). **골격 문서** — 불변식·상태전이는 매칭 작업 시 코드 확인 후 채운다.

## 용어
`MatchCandidate`(후보), `PersonalMatch`(1:1), `GroupMatch`(그룹), `GroupMatchMember`(그룹 구성원), `GroupMatchDecline`(그룹 매칭 거절).

## 불변식
- 후보 풀 자격(점수화 전 하드 필터, `isValidPair`): 성별 상호 선호(`QuizProgress.preferredGender`, 기본 `OPPOSITE`)와 나이차 ≤10 — 둘 다 대칭 조건이라 점수화 **전**에 걸러 살아남는 페어가 항상 대칭이게 한다. 성별·나이 미상 회원은 후보에서 제외.
- 점수: 퀴즈 답변 일치율(`MatchScoreCalculator`).
- 선발: 상위 20% + 동점 포함(`TopRatioSelector`) → 1인 5명 hard limit, 양방향 생존(양쪽 유지집합에 모두 있어야 노출)(`HardLimitApplier`).
- 동점 처리: 회원별 후보를 shuffle 후 점수 desc로 stable 정렬 — 점수가 다르면 결정적, 동점만 무작위(특정 회원이 체계적으로 유리해지는 것 방지). comparator 내부 random 금지(shuffle로 분리).
- 1:1 유니크: `PersonalMatch`는 `memberId1`=min/`memberId2`=max로 정규화 + `requesterId` 별도 보존. UK(`member_id_1`, `member_id_2`, `quiz_set_id`)로 방향 무관 중복 금지. 방향은 `receiverId()`/`counterpartOf()` 헬퍼로 복원.
- `match_candidate`: 페어당 양방향 2행(`ownerMemberId`/`otherMemberId`)으로 저장(내 후보 조회 단순화). 재계산은 `deleteByQuizSetId` 후 대체, anti-join 단일 쿼리 멱등 스케줄러(기본 매주 목 05:00, `test` 프로필 비활성).
- 그룹 유니크: `GroupMatchMember`(UK `room_id`+`member_id`) / `GroupMatchDecline`(UK `quiz_set_id`+`member_id`)로 분리. `GroupMatch`의 `quizSetId` UK 제거 → 퀴즈셋당 다수 그룹 허용(한 멤버가 같은 퀴즈셋 여러 방 참여 가능).
- 근거 ADR: `docs/adr/0007-matching-pure-pipeline.md`(순수 파이프라인·대칭 필터·동점 무작위), `docs/adr/0008-matching-entity-uniqueness-modeling.md`(페어 정규화·참여/거절 분리).

## 상태 전이
- 상태 enum: `match/entity/PersonalMatchStatus`(1:1). 그룹 참여/거절은 `GroupMatchMember`/`GroupMatchDecline` 두 테이블로 표현.

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/match/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/match/repository/` (+ `querydsl/`)
