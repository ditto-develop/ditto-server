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
- 같은 두 사람이 한 주에 여러 그룹에서 만나 양쪽 다 선택하면 `rematch` 행 두 개가 각각 `MATCHED`가 된다. 이는 **정상**이다(다른 만남에서 나온 다른 의사). 같은 쌍의 채팅방이 둘 열리는 것만 막으며, 그 판정은 **방을 만드는 `I2`가 담당**한다 — 성사 트랜잭션은 중복을 신경 쓰지 않는다([ADR 0013](../adr/0013-rematch-duplicate-at-room-creation.md)).

## 상태 전이

```text
WAITING (생성 시)
  → 양쪽 모두 true 확정: MATCHED (matchedAt 기록)
  → 양쪽 응답 완료 & 하나 이상 false: CANCELLED
```

- 전이는 나중에 도착한 `submitWants()` 호출이 같은 트랜잭션에서 수행한다. 별도 판정 API·배치는 없다.
- 취소 사유 컬럼은 두지 않는다. `CANCELLED`가 곧 "상호 선택이 아님"이고 `member1Wants`/`member2Wants`로 확인되므로 저장할 정보가 없다. 중복은 성사를 취소하지 않으므로([ADR 0013](../adr/0013-rematch-duplicate-at-room-creation.md)) 사유가 갈리지 않는다 — 정지·탈퇴처럼 값으로 구분해야 하는 사유가 생기는 `D1`에서 컬럼과 enum을 함께 도입한다.
- 재매칭 채팅의 방 ID·개방/종료 시각은 이 테이블에 두지 않는다. 방은 `chat_room.(source_type, source_id)` = (`REMATCH`, `rematch.id`)로 찾고, 개방·종료 시각은 `chat_room.opens_at`/`expires_at`이 SSOT다(`C1`·`I2`).
- 생성 호출자는 그룹 채팅 종료 어댑터다 — `RematchPairCreator`가 종료 시점 참여자 전원의 쌍(`N(N-1)/2`)을 멱등 생성한다. 제출 호출자는 리뷰 제출 API(A2)의 `RematchSubmitter`다([review 도메인](review.md)).
- **쌍은 평가보다 먼저 만들어져야 한다.** 그룹 평가는 재매칭 의사를 필수로 받고 `RematchSubmitter`가 쌍을 찾지 못하면 `INVALID_REVIEW_TARGET`으로 거부하므로, 순서가 뒤집히면 사용자가 평가를 다 채우고 제출에서 막힌다.

## 핵심 파일

- 엔티티: `domain/src/main/kotlin/com/ditto/domain/rematch/entity/`
- 리포지토리: `domain/src/main/kotlin/com/ditto/domain/rematch/repository/`
- 테스트 픽스처: `domain/src/testFixtures/kotlin/com/ditto/domain/rematch/RematchFixture.kt` (엔티티 직접 생성 대신 이 팩토리를 쓴다)
- 평가 제출과의 접점: `api/src/main/kotlin/com/ditto/api/review/service/RematchSubmitter.kt`(제출), `RematchPairCreator.kt`(생성)
- 마이그레이션: `domain/db/V20260726232700_재매칭 테이블 추가.sql`
