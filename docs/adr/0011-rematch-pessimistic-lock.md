# ADR 0011 — 재매칭 동시 제출: 비관적 행 잠금 도입

- 상태: Accepted
- 근거: Issue #109 착수 전 블로킹 결정 (2026-07-26 확정)

## Context

재매칭은 `rematch` 한 행의 두 컬럼(`member_1_wants`, `member_2_wants`)을 두 회원이 각자 최종 확정하고, 양쪽이 모두 true가 되는 순간 정확히 한 번 `MATCHED`로 전이해야 한다. 두 회원이 동시에 제출하면 각 트랜잭션이 스냅숏 읽기로 상대를 미응답으로 보고 둘 다 `WAITING`을 유지하는 성사 누락이 가능하다. 제출은 최종·수정 불가라 이 누락은 재시도로 복구되지 않는다.

기존 레포의 동시성 방어는 두 패턴뿐이고 비관적 잠금 선례는 없었다.

1. DB 유일키 + exists 선체크 (`ChatRoom (source_type, source_id)` 등)
2. 조건부 UPDATE — `MemberReportRepository.transitionReview`의 `where ... and status = :expected` + 반환 행 수로 승패 판정

조건부 UPDATE로도 정확성은 확보할 수 있으나, "내 값 기록 → 성사 전이 시도 → 행 수 판별 → 재조회"의 다단계가 되고 전이 UPDATE의 행 수 0이 오류가 아니라 세 가지 정상 경로(상대 미응답·상대 false·상대가 이미 성사시킴)로 갈라진다. 신고 검토의 "행 수 0 = 이미 처리됨 오류"라는 단순함이 여기서는 성립하지 않는다.

## Decision

pair 제출 경로는 `PESSIMISTIC_WRITE`(`SELECT ... FOR UPDATE`) 행 잠금으로 직렬화한다 — `RematchRepository.findWithLockById`. 나중에 도착한 트랜잭션이 앞 커밋의 상대 선택을 반드시 본 뒤 판정하므로, 잠금→읽기→판단→쓰기 한 흐름에서 성사 누락·중복 전이가 구조적으로 불가능하다.

새 패턴이므로 아래 성능 안전 규칙을 함께 강제한다. 이후 비관적 잠금 사용처는 이 규칙을 따른다.

1. 잠금은 PK·유일키 **단건 조회로만** 건다. 비인덱스 조건의 `FOR UPDATE`는 스캔한 행 전부를 잠근다.
2. 잠금 보유 트랜잭션은 짧게 유지하고 외부 I/O(알림 전송 등)를 포함하지 않는다. 후속 작업은 outbox로 분리한다.
3. 한 요청 트랜잭션이 잠그는 pair 행은 1개다. 복수 잠금이 필요하면(리뷰 과제 행 등) 잠금 순서를 고정한다 — 부모 리뷰 과제 → pair.
4. 일반 조회는 잠그지 않는다. MVCC 일관 읽기는 행 잠금에 대기하지 않으므로 조회 API는 영향받지 않는다.
5. 잠금 조회는 그 트랜잭션에서 해당 엔티티의 **첫 접근**이어야 한다. 같은 트랜잭션이 먼저 비잠금 조회를 했다면 영속성 컨텍스트에 이미 인스턴스가 있어, 이후 잠금 조회가 행에 잠금은 걸어도 필드는 낡은 값 그대로일 수 있다. 선행 조회를 피할 수 없으면 `refresh(entity, PESSIMISTIC_WRITE)`로 다시 읽는다.
6. 잠금 조회 메서드는 `@Transactional(propagation = MANDATORY)`로 선언한다. 호출자 트랜잭션이 없으면 잠금이 즉시 풀리고 엔티티가 준영속이 되어 이후 변경이 조용히 사라지는데, MANDATORY면 그 상황이 유실 대신 예외로 드러난다.
7. 잠금으로 지킨 판정에 **다른 테이블의 집계·조회가 끼면 그 조회도 잠금 읽기**여야 한다. 행 잠금은 그 행만 최신으로 만들 뿐, 같은 트랜잭션의 비잠금 SELECT 는 잠금 대기 **전에** 고정된 InnoDB 읽기 스냅샷(첫 비잠금 읽기 시점)을 계속 쓴다 — 방 행을 잠갔어도 멤버 카운트가 비잠금이면 대기 중 커밋된 이탈이 안 보인다(#142 그룹 이탈의 잔여 인원 판정 선례: `ChatRoomMemberRepository.findAllWithLockByRoomId`). 이 문제는 READ COMMITTED 인 H2 테스트로는 재현되지 않으므로 코드 리뷰로 강제한다.

## 검증 범위

동시 제출 테스트(`RematchTest`의 "동시 제출")는 H2에서 돈다. H2는 "잠금이 판정을 직렬화한다"는 계약 위반을 잡아주지만 InnoDB의 잠금 시맨틱과 동일하지는 않다. 프로덕션 MySQL에서 이 잠금이 처음 실제로 쓰이는 시점은 리뷰 제출 API(`A2`)이므로, 그 PR에서 MySQL 기준 동시 제출 검증을 함께 수행한다. 그 전까지 이 ADR의 성능 규칙은 코드 리뷰로만 강제된다.

## Consequences

- 얻음: 상호 성사 판정이 한 트랜잭션·한 흐름으로 끝나고, 동시 제출에서도 성사를 놓치거나 채팅 예약이 중복될 수 없다.
- 비용: 같은 pair 행을 같은 순간 제출하는 경우에만 뒤 트랜잭션이 앞 커밋까지 대기한다(정상적으로 수 ms). 잠금 보유가 길어지면 대기가 DB lock wait timeout까지 늘 수 있으므로 위 규칙 준수가 전제다.
- 레포 첫 비관적 잠금이므로 조건부 UPDATE 관례와 사용 기준이 갈린다 — 단일 상태 전이·패자 오류 처리에는 기존 조건부 UPDATE, 읽기 결과에 따른 분기 판정이 잠금 구간 안에 있어야 하면 비관적 잠금.

## Links

- [Issue #109](https://github.com/ditto-develop/ditto-server/issues/109)
- `domain/src/main/kotlin/com/ditto/domain/rematch/repository/RematchRepository.kt`
- `domain/src/main/kotlin/com/ditto/domain/rematch/entity/Rematch.kt` (`submitWants`)
- 조건부 UPDATE 선례: `domain/src/main/kotlin/com/ditto/domain/memberreport/repository/MemberReportRepository.kt`
- 페어 정규화 패턴: [ADR 0008](0008-matching-entity-uniqueness-modeling.md)
