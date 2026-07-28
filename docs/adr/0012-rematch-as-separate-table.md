# ADR 0012 — 재매칭을 리뷰 응답 컬럼이 아닌 독립 테이블로 모델링

- 상태: Accepted
- 근거: Issue #109 착수 시 대안 검토 (2026-07-26 확정)

## Context

재매칭 의사는 그룹 평가 제출(`A2`)과 같은 트랜잭션에서 확정된다. 그래서 가장 작은 설계는 별도 테이블 없이 `review_answer`에 `wants_rematch` 컬럼 하나를 두고, 상호 여부는 상대의 응답 행을 조회해 판정하는 것이다. 기존 `PersonalMatch` 재사용도 후보였다. 두 대안 모두 아래 지점에서 무너진다.

**`review_answer` 컬럼 안** — 상호 판정에 필요한 두 값이 서로 다른 두 행(A의 리뷰 밑 행, B의 리뷰 밑 행)에 흩어지고 각각 다른 트랜잭션이 쓴다. 잠글 단일 지점이 없어 [ADR 0011](0011-rematch-pessimistic-lock.md)이 막은 성사 누락이 되살아난다. 또한 `matched_at`·`rematch_chat_room_id`·`chat_opens_at`은 두 사람에게 공통인 하나의 사실인데 리뷰 응답 행은 각자 소유라 어느 쪽에 쓸지가 임의 결정이 된다. 수명도 다르다 — `O2`의 리뷰 무효화가 이미 성사된 재매칭까지 취소해선 안 되고, `D1`(탈퇴)·`I2`(채팅 예약)는 재매칭만 건드려야 하는데 같은 행에 있으면 분리할 수 없다. 마지막으로 내 제출 트랜잭션이 상대의 리뷰 본문 행을 조회하게 되어 비공개 데이터 접근 경로가 열린다.

**`PersonalMatch` 재사용** — 불변식이 정반대다. `PersonalMatchResponse.requesterId`는 sent/received 목록에 그대로 실려 나가고("누가 요청했는지 보이는 것"이 그 모델의 존재 이유), 재매칭은 상대의 선택이 절대 보이지 않아야 한다. 같은 테이블에 두면 노출돼야 하는 행과 노출되면 안 되는 행이 한 조회 경로를 공유한다. 구조적으로도 UK `(member_id_1, member_id_2, quiz_set_id)`가 걸린다 — ⑧-1에서 재매칭 채팅은 기존 1:1 채팅과 공존하기로 확정했으므로 같은 두 사람이 같은 퀴즈셋에서 두 관계를 동시에 가져야 한다.

## Decision

재매칭은 `rematch` 테이블에 한 쌍당 한 행으로 저장한다. 한 행이 양쪽의 단방향 의사(`member_1_wants`/`member_2_wants`)와 성사 결과(`status`·`matched_at`)를 담으므로, 상호 판정에 필요한 상태가 한 곳에 모이고 그 행을 잠그는 것으로 동시 제출이 직렬화된다.

담지 않는 것도 명시한다. 취소 사유는 지금 `CANCELLED`에서 유일하게 도출되므로 컬럼을 두지 않고, 값으로 구분해야 할 사유가 생기는 트랙(`R2`·`D1`)에서 도입한다. 재매칭 채팅의 방 ID·개방/종료 시각도 두지 않는다 — 방은 `chat_room.(source_type, source_id)`로 찾고 시각은 `chat_room.opens_at`/`expires_at`이 SSOT라, 여기에 복제하면 정의가 두 곳으로 갈라진다.

리뷰 도메인을 참조하지 않는다 — 재매칭을 아는 쪽은 이 테이블을 쓰는 호출자(리뷰 제출 API `A2`, 그룹 종료 어댑터 `I1G`)다. 덕분에 리뷰 코어(`E1`)와 병렬로 개발할 수 있다.

## Consequences

- 얻음: 상호 판정·성사 결과·채팅 예약이 한 행에 모여 잠금 한 번으로 정합성이 보장된다. 리뷰 무효화·탈퇴·채팅 예약이 서로 독립적으로 동작한다. 리뷰 코어와 병렬 개발이 가능하다.
- 비용: 그룹 종료 시 참여자 쌍 `N(N-1)/2`건을 미리 생성해야 하고, 대부분의 행은 아무도 선택하지 않은 채 종결된다. 소규모 그룹이라는 제품 제약이 전제다.
- 재매칭 의사를 제출하는 API는 리뷰 제출과 같은 트랜잭션에서 두 테이블을 함께 쓰므로, 잠금 순서를 리뷰 과제 → 재매칭으로 고정한다([ADR 0011](0011-rematch-pessimistic-lock.md) 규칙 3).
- 그룹 전체를 다시 모으는 재소집은 이 모델로 표현할 수 없다. 쌍이 아닌 부분집합·정족수·익명성 정책이 필요하므로 별도 기획 결정과 설계가 전제된다.

## Links

- [Issue #109](https://github.com/ditto-develop/ditto-server/issues/109)
- [ADR 0011](0011-rematch-pessimistic-lock.md) — 이 테이블의 동시 제출 잠금 규칙
- [ADR 0008](0008-matching-entity-uniqueness-modeling.md) — 페어 정규화 패턴
- `domain/src/main/kotlin/com/ditto/domain/rematch/entity/Rematch.kt`
- `docs/domains/rematch.md`
