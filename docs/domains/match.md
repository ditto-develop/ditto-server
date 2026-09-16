# match 도메인

> ⚠️ 이 도메인 코드를 수정하며 새 불변식·상태전이를 확인했으면 떠나기 전에 아래를 채워라.

매칭(1:1 / 그룹).

## 용어
`MatchCandidate`(1:1 후보), `PersonalMatch`(1:1 성사), `GroupMatch`(후보 그룹이자 성사 단위), `GroupMatchMember`(그룹 구성원), `InvitationStatus`(초대 응답 상태).

## 불변식
- **후보를 담는 곳이 타입마다 다르다.** 1:1은 `match_candidate`(페어 2행), 그룹은 `group_match` + `group_match_member`. 배치 대상 anti-join(`findEndedQuizSetsWithoutCandidates`)도 양쪽을 다 봐야 한다 — 한쪽만 보면 그 타입 퀴즈셋이 매주 재계산된다.
- 1:1 후보 풀 자격(점수화 전 하드 필터, `isValidPair`): 성별 상호 선호(`QuizProgress.preferredGender`, 기본 `OPPOSITE`)와 나이차 ≤10 — 둘 다 대칭 조건이라 점수화 **전**에 걸러 살아남는 페어가 항상 대칭이게 한다.
- **성별·나이 미상 회원은 풀에서 빼지 않는다.** `MatchParticipant.gender·age`가 nullable 이고, 그 조건을 쓰는 1:1만 자격 미달로 거른다. 그룹은 두 조건을 쓰지 않으므로 미상 회원도 그대로 후보가 된다.
- 점수: 퀴즈 답변 일치율(`MatchScoreCalculator`).
- 1:1 선발: 상위 20% + 동점 포함(`TopRatioSelector`) → 1인 5명 hard limit, 양방향 생존(양쪽 유지집합에 모두 있어야 노출)(`HardLimitApplier`).
- 동점 처리: 회원별 후보를 shuffle 후 점수 desc로 stable 정렬 — 점수가 다르면 결정적, 동점만 무작위(특정 회원이 체계적으로 유리해지는 것 방지). comparator 내부 random 금지(shuffle로 분리).
- 1:1 유니크: `PersonalMatch`는 `memberId1`=min/`memberId2`=max로 정규화 + `requesterId` 별도 보존. UK(`member_id_1`, `member_id_2`, `quiz_set_id`)로 방향 무관 중복 금지. 방향은 `receiverId()`/`counterpartOf()` 헬퍼로 복원.
- `match_candidate`: 페어당 양방향 2행(`ownerMemberId`/`otherMemberId`)으로 저장(내 후보 조회 단순화). 재계산은 `deleteByQuizSetId` 후 대체, anti-join 단일 쿼리 멱등 스케줄러(기본 매주 목 05:00, `test` 프로필 비활성).
- 그룹 유니크: `GroupMatchMember`(UK `room_id`+`member_id`). `GroupMatch`의 `quizSetId` UK 없음 → 퀴즈셋당 다수 그룹, 한 멤버가 같은 퀴즈셋의 여러 후보 그룹에 속할 수 있다(최대 3개 노출).
- **매칭이 다루는 퀴즈셋은 이번 운영 주 것뿐이다**(`MatchWeekPolicy`, [ADR 0026](../adr/0026-matching-scoped-to-operation-week.md)). 기준은 "조회자가 **이번 운영 주에** 완주한 해당 타입 퀴즈셋"(`findCompletedQuizSetInWeek`)이며, 주차 무관 조회는 레포에 없다. 후보 행은 지난 주 것도 남으므로 주차로 좁히지 않으면 지난 사이클 후보가 계속 노출된다 — 1:1·그룹이 타입별로 독립해 최신 셋을 찾기 때문에 한쪽만 이번 주인 응답이 섞인다. 후보 응답은 `weekStartedOn` + `year`/`month`/`week`를 함께 내려준다(ADR 0010 규약).
- **응답 경로도 이번 주만 받는다.** 그룹 수락·거절, 1:1 요청·수락·거절은 대상 퀴즈셋이 이번 주가 아니면 `NOT_MATCHING_PERIOD`(5008) — 화면에서 감추는 것만으로는 지난 주 그룹이 오늘 성사돼 채팅방이 열리는 것을 못 막는다. 그룹 수락 마감이 사실상 일요일 자정이 된다.
- 후보 관계는 **성사 전 열람 권한**의 근거이기도 하다: `MatchAccessChecker.isMatchCandidate`가 위와 같은 기준 퀴즈셋의 후보로 판정한다. 1:1은 `match_candidate` 페어 행(방향 무관, `existsPairByQuizSetId`), 그룹은 같은 후보 그룹에 양쪽이 거절하지 않고 남아 있는지(`existsSharedCandidateGroup`). 이 판정을 쓰는 경로가 같아야 한다 — 어긋나면 후보 카드는 보이는데 소개노트는 403이 된다. 실제로 프로필·평점이 이 판정에서 빠져 있어 후보 평점 조회가 전부 403이었고, [ADR 0029](../adr/0029-peer-profile-candidate-summary-tier.md)에서 같은 모델로 합쳤다. 공개 범위는 `docs/domains/intronote.md`·`docs/domains/review.md` 참고.

- 근거 ADR: `docs/adr/0007-matching-pure-pipeline.md`(순수 파이프라인·대칭 필터·동점 무작위), `docs/adr/0008-matching-entity-uniqueness-modeling.md`(페어 정규화·참여/거절 분리), `docs/adr/0025-intro-note-candidate-preview.md`(후보 기반 성사 전 열람), `docs/adr/0026-matching-scoped-to-operation-week.md`(이번 운영 주로 고정).

### 그룹 매칭

- **선발**: 참여자 한 명씩을 씨앗으로 잡아 자기와 점수 높은 순으로 정원을 채운다(`GroupMatchingProcessor`). 같은 멤버 조합은 하나로 합치고(`Set` 동일성), 그룹 점수 상위 20%(동점 포함) → 1인 3개로 좁힌다. 선발 두 단계는 1:1과 같은 컴포넌트를 쓴다.
- **노출 보장**: 선발에서 밀려 어느 그룹에도 못 든 회원은 자기가 속한 최고점 씨앗 그룹으로 덮는다. 1:1의 모집단은 페어 N(N−1)/2개인데 그룹은 씨앗당 하나, 즉 약 N개뿐이라 같은 20%라도 남는 수가 훨씬 적고 살아남은 그룹끼리 크게 겹친다 — 덮지 않으면 30명 중 20여 명이 빈 화면을 본다. 이 덮기는 1인 3개 상한을 넘길 수 있다(화면은 하나만 보여주므로 실질 영향이 없다).
- **정원**(`GroupSizePolicy`): 풀 <10 → min(6, 풀) / <30 → 5 / 그 외 → 4. 기획이 범위로 준 구간은 큰 쪽 — 성사가 3명 이상 수락이라 정원이 클수록 한 명 이탈을 견딘다. 최소 3명.
- **성별·나이 하드 필터가 없다.** 기획에 없고, 성별이 둘뿐이라 3명 이상이 서로 전부 이성인 조합은 존재할 수 없다. 차단만 반영해 차단 관계인 두 사람을 같은 그룹에 넣지 않는다.
- **점수가 두 종류다.** 선발용은 구성원 **모든 페어** 점수의 평균(`group_match.score`, 저장). 화면 표시용은 **나와 각 구성원**의 일치 문항 수 평균으로 다른 값이라 조회 시점에 계산한다(`GroupCandidateService`).
- **상태 전이**: 배치가 `group_match`(비활성·score) + `group_match_member`(`PENDING`)로 깔고, 각자 수락·거절한다. 수락자가 3명에 닿으면 성사(`isActive`)되고 금요일에 채팅방이 열린다. 채팅방에는 **수락한 사람만** 들어간다. 수락·거절 모두 되돌릴 수 없다.
- **성사되지 못한 그룹은 상태를 바꾸지 않고 알림만 낸다**(`UnformedGroupNotifier`). 마감(그 주 금요일 00:00 — 채팅 개방과 같은 순간)까지 3명에 못 닿으면 수락자에게 `GROUP_NOT_FORMED`를 보낸다. 취소 플래그를 따로 두지 않는 이유: `is_active=false`가 이미 "성사되지 않음"이고, 재발송은 알림 유형의 `ONCE_PER_TARGET`(대상 = `group_match.id`)이 막아 상태 없이도 멱등이다. 알림이 없던 동안 수락자는 채팅방이 왜 안 열리는지 알 수 없었다(QA BUG-071).
- **자동 거절**: 한 그룹을 수락하면 같은 퀴즈셋의 남은 `PENDING` 초대가 모두 `DECLINED`가 된다. 한 주에 열리는 채팅방이 하나뿐이라서다. 거절당한 그룹의 다른 구성원에게는 알리지 않는다.
- **수락 경로는 방 행을 비관적 잠금**한다([ADR 0011](../adr/0011-rematch-pessimistic-lock.md)). 잠금이 없으면 동시 수락이 각자 낡은 수락자 수를 보고 둘 다 채팅방을 만들려다 `chat_room (source_type, source_id)` 유일키에 걸려 한쪽 트랜잭션이 통째로 롤백된다. 잠금 조회가 트랜잭션 **첫 접근**이어야 한다(규칙 5).
- **후보 재생성은 응답이 시작되면 거부한다**(`GroupCandidateWriter` → `MATCH_CANDIDATES_ALREADY_RESPONDED`, 기존 후보는 그대로). `group_match` 하나가 후보이자 성사 상태라, 지우면 열린 채팅방이 가리킬 곳을 잃는다. 조용히 건너뛰지 않고 예외로 알리는 이유: 어드민이 재생성을 눌렀는데 성공처럼 보이면 안 된다.
- **후보 생성은 퀴즈셋마다 자기 트랜잭션**이다 — `generateMatchingCandidates`(`@Transactional`)를 배치(`MatchingBatchFacade.runScheduledMatching`)가 **트랜잭션 없이** 프록시로 부른다. facade 나 그 호출자에 `@Transactional`을 붙이면 격리가 깨진다. 배치는 셋을 돌며 실패는 경고 로그만 남기고 계속한다([ADR 0027](../adr/0027-matching-batch-per-quiz-set-transaction.md)). 후보가 없는 셋만 고르지만(anti-join) 대상 선정 직후 어드민 재생성·수락이 끼어들면 위 예외를 만날 수 있고, 그때 다른 셋의 후보까지 롤백되면 안 된다. 실패한 셋은 다음 배치가 다시 집고, 반환하는 ID(알림 대상)는 성공한 셋만이다.
- **재생성 결과는 저장하지 않는다.** `generateMatchingCandidates`가 `CandidateGenerationSummary`(후보 풀 인원·삭제/저장 행 수·매칭 목록)를 돌려주고, 어드민 화면은 flash로 한 번 보여주며 REST(`/api/v1/admin/quiz-sets/{id}/matching/regenerate`)는 `data`에 실어 준다. 서버 로그(info)에도 같은 내용을 남긴다.
- `group_match_decline` 테이블은 남아 있으나 코드가 쓰지 않는다 — 거절은 `InvitationStatus.DECLINED`로 그룹별로 남는다.

## 상태 전이
- 상태 enum: `match/entity/PersonalMatchStatus`(1:1), `match/entity/InvitationStatus`(그룹 초대: `PENDING` → `ACCEPTED`/`DECLINED`, 되돌릴 수 없음).

## 핵심 파일
- 엔티티: `domain/src/main/kotlin/com/ditto/domain/match/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/match/repository/` (+ `querydsl/`)
