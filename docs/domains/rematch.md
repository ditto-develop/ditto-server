# rematch 도메인

재매칭 — 그룹 채팅 종료 후 멤버끼리 "1:1로 다시 만나고 싶어요"를 비공개로 선택하고, 상호 선택일 때만 별도 1:1 관계가 성사된다.

## 용어

기획 용어 **"그룹 재매칭"** = 코드의 `Rematch`다. 이름에 출처(`Group`)도 결과(`Personal`/`OneToOne`)도 넣지 않는다 — `GroupRematch`는 "그룹을 다시 짠다"로 오독되고 `GroupMatch`의 변형처럼 읽히며, `PersonalRematch`는 1:1에서 출발한 재매칭만 가능한 것처럼 읽히기 때문이다. 출처는 `sourceGroupMatchId` 컬럼이, 결과가 1:1이라는 사실은 `memberId1`/`memberId2` 두 컬럼 구조가 드러낸다.

`Rematch`(소스 그룹의 두 멤버로 정규화된 비순서 쌍 — 단방향 의사와 성사 결과를 한 행에 보관).

리뷰 응답 컬럼이나 `PersonalMatch`가 아니라 독립 테이블인 이유는 [ADR 0012](../adr/0012-rematch-as-separate-table.md)에 있다.

## 불변식

- 쌍 정규화: `memberId1`=min, `memberId2`=max ([ADR 0008](../adr/0008-matching-entity-uniqueness-modeling.md) 패턴). UK(`source_group_match_id`, `member_id_1`, `member_id_2`)로 같은 소스 그룹의 방향 무관 중복 금지. 자기 자신과의 쌍 금지.
- 단방향 선택은 비공개: 상대의 제출 여부·값·시각을 어떤 경로로도 노출하지 않는다. 두 선택 필드는 `private`이라 외부 조회는 본인 값만 주는 `wantsOf()`뿐이고, `submitWants()`는 인가 → 본인 상태 → 쌍 상태 순으로 검사한다. 순서를 뒤집으면 실패 응답의 오류 코드 차이만으로 상대의 제출 시각을 특정할 수 있다.
- 선택 제출은 최종: `NULL`(미응답) → `true`/`false` 한 번만. 재제출 거부.
- 소속 운영 주는 `OperationWeek`로 받는다. "월요일만 허용"은 그 값 객체가 강제하므로 재매칭 쪽에서 다시 검증하지 않고, `QuizSet`처럼 컬럼은 `weekStartedOn: LocalDate`로 저장하고 `operationWeek` 접근자로 되돌린다([ADR 0010](../adr/0010-week-identifier-week-started-on.md)). 이 컬럼은 원본 추적용이지 제한 키가 아니다.
- 제출 경로는 pair 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 판정한다 — 동시 제출의 성사 누락 방지 ([ADR 0011](../adr/0011-rematch-pessimistic-lock.md)의 안전 규칙 준수).
- 횟수 제한은 없다(2026-07-27 기획 확인) — 한 주에 여러 명과 성사 가능하고, 같은 상대와 다른 주말에 다시 성사되는 것도 허용한다.
- 같은 두 사람이 여러 그룹에서 만나 양쪽 다 선택하면 `rematch` 행 두 개가 각각 `MATCHED`가 되고, 두 성사가 같은 주말로 향하면 **채팅방도 둘 열린다** — 중복을 막지 않는다([ADR 0017](../adr/0017-rematch-duplicate-room-allowed.md)이 [ADR 0013](../adr/0013-rematch-duplicate-at-room-creation.md)을 대체). `GroupMatchService`의 `existsByMemberIdAndQuizSetId`는 **퀴즈셋 단위** 가드라 주가 다른 두 그룹은 막지 않고, 방이 열릴 주말은 그룹의 주가 아니라 **성사 시각**이 정한다 — 평가 제출에 기한이 없어 두 평가를 같은 주에 몰아 제출하면 두 성사가 같은 금요일로 향한다. 조건이 좁아 감수하며, 막으려면 건너뛴 쌍이 예약 조회에 영구 잔류하는 문제를 떠안는다(근거는 ADR 0017).

## 상태 전이

```text
WAITING (생성 시)
  → 양쪽 모두 true 확정: MATCHED (matchedAt 기록)
  → 양쪽 응답 완료 & 하나 이상 false: CANCELLED (NOT_MUTUAL)
  → 한쪽이 탈퇴: CANCELLED (MEMBER_LEFT)
```

- 전이는 나중에 도착한 `submitWants()` 호출이 같은 트랜잭션에서 수행한다. 별도 판정 API·배치는 없다.
- **취소 사유는 `cancel_reason`으로 저장한다**(`NOT_MUTUAL`·`MEMBER_LEFT`). 사유가 "상호 선택이 아님" 하나뿐일 때는 `status`에서 도출돼 컬럼을 두지 않았으나(ADR 0012), 탈퇴가 두 번째 사유가 되면서 값으로 구분해야 한다 — 두 선택 값만으로는 탈퇴를 알 수 없다. 중복은 성사를 취소하지 않으므로([ADR 0017](../adr/0017-rematch-duplicate-room-allowed.md)) 사유가 되지 않는다.
- 재매칭 채팅의 방 ID·개방/종료 시각은 이 테이블에 두지 않는다. 방은 `chat_room.(source_type, source_id)` = (`REMATCH`, `rematch.id`)로 찾고, 개방·종료 시각은 `chat_room.opens_at`/`expires_at`이 SSOT다(`C1`·`I2`). 성사된 모든 쌍이 자기 방을 얻으므로(ADR 0017) 이 튜플 조회는 언제나 성립한다.
- 방은 성사 트랜잭션이 만들지 않는다. `RematchChatRoomOpener`가 "성사됐는데 방이 없는 쌍"을 찾아 예약한다.
- **개방은 성사 이후 처음 오는 금요일 00:00이다**(`ChatPeriod.upcomingWeekendFrom`). 기획이 "금요일 00:00 채팅방 오픈"으로만 정해 특정 주말을 약속하지 않으므로, **진행 중인 주말에 합류시키지 않는다** — 주말 도중에 성사돼도 남은 몇 시간에 급히 열지 않고 온전한 72시간을 받는 다음 금요일로 간다. 일반 매칭 채팅이 진행 중 주말에 합류하는 것(`weekendOf`)과 다른 점이다.
  - 그 덕에 **이미 닫힌 창이 계산될 수 없다.** 합류시키면 일요일 늦은 밤 성사(성사를 확정하는 것이 평가 제출이다)에서 방이 몇 분짜리로 열리거나, 예약이 도는 순간 창이 닫혀 `ACTIVE`로 태어난 뒤 곧바로 만료된다. 후자는 방이 존재하는 탓에 예약 조회가 완료로 판정해 다시 고치지 않아 **조용한 유실**이 된다(재매칭은 평가도 열지 않아 보상 경로가 없다).
  - 기준은 `max(성사 시각, 예약 시점)`이다. 예약이 밀려 성사 다음 금요일마저 지났다면 그 금요일에는 열 수 없고, 열 수 있는 가장 이른 금요일이 답이다. 방 생성을 성사 트랜잭션에 묶지 않는 이유는 성사가 평가 제출 중에 확정되기 때문이다 — 방 생성 실패가 평가 제출을 되돌리면 안 된다.
- **탈퇴는 재매칭을 두 갈래로 처리한다** — 미성사(`WAITING`) 쌍은 취소하고, 이미 성사된 쌍은 탈퇴 자체를 막는다.
  - 미성사 취소는 `LeftMemberRematchCanceller`가 **탈퇴 트랜잭션 안에서** 한다. 취소하지 않으면 남은 한쪽이 나중에 제출해 `MATCHED`가 되고, 방 예약이 탈퇴자와의 채팅방을 만든다.
  - 방 예약과 달리 **원자적이어야 한다.** 방 예약은 실패해도 다음 스케줄러 주기가 복구하지만, 이 취소가 따로 실패하면 탈퇴는 확정된 채 쌍이 남아 복구할 주체가 없다.
  - **예약 조회에서 걸러내는 방식으로는 막을 수 없다.** 그 쌍이 조회 조건을 영구히 만족해 배치 앞자리를 점유하고, 완전 삭제는 `member`만 지우므로 `rematch` 행이 영구히 남는다. 상태를 값으로 바꿔야 조회에서 자연히 빠진다.
  - 대상은 **ID 로 찾고 행을 잠근 뒤 다시 판정한다.** 엔티티를 먼저 읽으면 잠금 조회가 낡은 값을 돌려주고(ADR 0011 규칙 5), 그 값으로 판정하면 그 사이 상대가 커밋한 성사를 덮는다. 잠금 순서는 `id` 순으로 고정한다(규칙 3).
  - 성사된 쌍은 되돌리지 않는다 — 통보된 성사가 사라진다. 방이 생기기 전 구간은 `LeaveProgressChecker`가 막고, 방이 생긴 뒤에는 "끝나지 않은 방" 조건이 이어받는다(`MATCHED`만 보고 막으면 안 되는 이유는 그 조회의 KDoc 에 있다).
  - 두 방어선을 지나도 남는 좁은 창(가드 통과 후 상대가 제출)이 있어, `RematchChatRoomOpener`가 예약 직전 탈퇴자 여부를 한 번 더 본다.
- **탈퇴로 취소된 쌍에 대한 평가 제출은 거부하지 않는다.** 재매칭 의사만 버리고 평가는 정상 확정시킨다(`RematchSubmitter`) — 거부하면 남은 회원이 그 대상 평가를 영구히 확정할 수 없어 그룹 평가가 미완료로 남는다.
- **복구(30일 내 재가입)는 취소를 되돌리지 않는다.** 두 회원이 모두 `ACTIVE`로 돌아와도 그 그룹 출처의 재매칭은 성사되지 않는다.
- 생성 호출자는 그룹 채팅 종료 어댑터다 — `RematchPairCreator`가 종료 시점 참여자 전원의 쌍(`N(N-1)/2`)을 멱등 생성한다. 제출 호출자는 리뷰 제출 API(A2)의 `RematchSubmitter`다([review 도메인](review.md)).
- **쌍은 평가보다 먼저 만들어져야 한다.** 그룹 평가는 재매칭 의사를 필수로 받고 `RematchSubmitter`가 쌍을 찾지 못하면 `INVALID_REVIEW_TARGET`으로 거부하므로, 순서가 뒤집히면 사용자가 평가를 다 채우고 제출에서 막힌다.

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/rematch/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/rematch/repository/`
- 테스트 픽스처: `domain/src/testFixtures/kotlin/com/ditto/domain/rematch/RematchFixture.kt` (엔티티 직접 생성 대신 이 팩토리를 쓴다)
- 평가 제출과의 접점: `api/src/main/kotlin/com/ditto/api/review/service/RematchSubmitter.kt`(제출), `RematchPairCreator.kt`(생성)
- 채팅 예약과의 접점: `api/src/main/kotlin/com/ditto/api/rematch/service/RematchChatRoomOpener.kt`, 예약 대상 조회는 `domain/.../rematch/repository/querydsl/`
- 탈퇴와의 접점: `api/src/main/kotlin/com/ditto/api/user/service/LeftMemberRematchCanceller.kt`(미성사 쌍 취소), `LeaveProgressChecker.kt`(성사됐는데 방 없는 쌍이면 탈퇴 거부)
- 마이그레이션: `domain/db/V20260726232700_재매칭 테이블 추가.sql`, 예약 조회 인덱스는 `V20260804202238_재매칭 방 예약 조회용 인덱스 추가.sql`, 취소 사유 컬럼은 `V20260807195824_재매칭 취소 사유 컬럼 추가.sql`
