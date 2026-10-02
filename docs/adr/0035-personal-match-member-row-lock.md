# ADR 0035 — 1:1 신청·수락을 회원 행 잠금으로 줄 세운다 (수락은 facade로 두 단계)

- 상태: Accepted
- 근거: Issue #235 (2026-10-02 QA — 1:1 성사자가 다른 신청 푸시를 계속 받음, 성사자가 다른 신청을 수락하면 방이 둘 생김)

## Context

#235에서 "한 사람은 퀴즈셋당 1:1 성사 하나"를 신청·수락의 검사로 막고, 성사되면 두 사람이 그 퀴즈셋에서 주고받은 남은 `PENDING`을 `CANCELLED`로 정리하기로 했다. 검사와 정리는 둘 다 "이 사람이 지금 성사됐는가"를 읽고 판단한다. 같은 사람이 낀 트랜잭션 둘이 겹치면 각자 상대의 커밋 전 상태를 읽는다.

R이 X와 Y에게 신청해 두고 X와 Y가 거의 동시에 수락하면 이렇게 된다.

- **교착**: 각 수락이 자기 행을 ACCEPTED로 바꾼 뒤 상대 행을 CANCELLED로 바꾸려 해 서로를 기다린다. DB가 한쪽을 롤백하고 그 사용자는 서버 오류를 받는다. H2 테스트에서 `Deadlock detected`로 재현했다.
- **상태 덮어쓰기**: 한쪽이 상대 커밋 전 스냅샷으로 진행하면, 이미 CANCELLED가 된 행을 ACCEPTED로, 방이 열린 ACCEPTED 행을 CANCELLED로 덮는다. `PersonalMatch`에 `@Version`이 없고 Hibernate가 `where id=?`만 붙인 전체 컬럼 UPDATE를 보내기 때문이다. 덮이면 방이 있는 사람이 미성사로 보여 방이 더 생길 수 있다.
- **정리 누락**: A가 수락하는 동안 C가 A에게 신청하면, 수락의 정리 조회가 C의 미커밋 행을 못 봐 PENDING이 남고 A에게 신청 푸시가 간다.

다른 두 사람이 겹치는 경우라 FE의 연타 방지로는 막을 수 없다.

## Decision

**신청·수락은 두 회원의 `member` 행을 id 오름차순으로 잠근 뒤 판단한다.** 잠금은 기존 `MemberRepository.findWithLockById`(PK 단건, `MANDATORY`)를 쓴다. 같은 사람이 낀 신청·수락은 그 사람의 행에서 줄을 서고, 뒤 트랜잭션은 앞 커밋을 본 뒤 검사한다.

- **잠금 순서는 회원 → 매칭 행이다.** 매칭 행을 쥔 채 회원 잠금을 기다리면, 앞선 수락의 정리 UPDATE가 그 매칭 행을 기다려 교착이 된다. 매칭 행의 쓰기는 회원 잠금을 쥔 뒤에만 일어난다.
- **잠금은 트랜잭션의 첫 조회다.** ADR 0011 규칙 7의 예외(잠금 읽기가 첫 문장이면 이후 비잠금 조회가 안전)에 해당해 격리 수준은 기본(REPEATABLE READ) 그대로다.
- **신청**은 회원 id를 요청에서 바로 알아 그대로 첫 조회로 잠근다.
- **수락**은 잠글 회원을 알려면 매칭부터 읽어야 한다. 같은 트랜잭션에서 읽으면 그 조회 시점에 스냅샷이 고정돼, 잠금을 기다리는 동안 커밋된 성사를 이후 검사가 못 본다. 그래서 `PersonalMatchFacade`(트랜잭션 없음)가 두 회원 id만 먼저 읽고, `PersonalMatchService.acceptMatch`(트랜잭션)가 그 id로 회원 잠금부터 시작한다. `MatchingBatchFacade`(ADR 0027)와 같은 구조다.
  - facade에는 `@Transactional`을 붙이지 않는다. 붙이면 id 조회가 수락 트랜잭션과 합쳐진다.
  - id 조회는 엔티티가 아니라 값만 읽는다(`findPairMemberIdsById`). OSIV로 요청 동안 영속성 컨텍스트가 유지돼, 엔티티로 읽으면 수락 트랜잭션이 그 낡은 인스턴스를 다시 쓴다.
  - `acceptMatch`는 잠글 회원 id를 인자로 받는다. facade를 거치지 않으면 부를 수 없게 하려는 것이고, 받은 id가 매칭과 다르면 수락하지 않는다.
- 거절은 잠그지 않는다. 겹쳐도 같은 행을 REJECTED와 CANCELLED 중 하나로 덮을 뿐이고 둘 다 끝난 상태다.

### 버린 대안

- **매칭 행을 먼저 잠그고 회원을 잠금**: 위의 교착이 남는다.
- **`@Version` 낙관적 잠금**: 마이그레이션이 필요하고, 충돌한 쪽을 실패로 돌려줄 오류 코드를 따로 이어야 한다. 교착도 남는다.
- **수락만 READ COMMITTED**: 문장마다 최신 커밋을 읽어 한 트랜잭션으로 끝나지만, 레포에 없던 격리 수준 변경이 생기고 MySQL `binlog_format=STATEMENT`면 쓰기가 거부된다. H2는 원래 READ COMMITTED라 테스트로 차이도 안 보인다.
- **정리를 수락 커밋 뒤 별도 트랜잭션으로**: 수락 트랜잭션이 다른 사람 행을 건드리지 않아 매칭 행부터 잠가도 교착이 없다. 대신 정리가 실패하면 대기 신청이 남는다.
- **잠그지 않고 남은 위험으로 둠**: 위 세 결과가 모두 남고, 덮어쓰기는 데이터를 틀어지게 한다.

## Consequences

- 같은 사람이 낀 신청·수락은 수 ms씩 기다린다. 다른 사람끼리는 영향이 없다.
- `member` 행을 잠그는 곳이 가입 완료 하나에서 셋으로 늘었다. 회원 행을 오래 쥐는 트랜잭션이 생기면 신청·수락이 그만큼 기다린다(ADR 0011 규칙 2).
- 수락은 facade를 거쳐야 한다. 매칭을 한 번 더 읽는 비용(PK 조회 하나)이 든다.
- H2는 기본이 READ COMMITTED라 RR 스냅샷 문제는 테스트로 재현되지 않는다(ADR 0011 검증 범위와 같다). 동시 수락 테스트는 잠금이 없으면 교착으로 실패하는 것만 확인한다.
- 같은 사람이 같은 신청을 수락·거절로 동시에 누르는 경우는 그대로다. 수락이 먼저 읽은 매칭 인스턴스로 진행해 거절을 덮을 수 있다. FE가 버튼 연타를 막는다.

## Links

- [Issue #235](https://github.com/ditto-develop/ditto-server/issues/235)
- [ADR 0011](0011-rematch-pessimistic-lock.md) — 비관적 잠금 규칙
- `api/src/main/kotlin/com/ditto/api/match/service/PersonalMatchFacade.kt`
- `api/src/main/kotlin/com/ditto/api/match/service/PersonalMatchService.kt`
- [ADR 0027](0027-matching-batch-per-quiz-set-transaction.md) — 트랜잭션 없는 facade가 프록시로 부르는 선례
- `domain/src/main/kotlin/com/ditto/domain/member/repository/MemberRepository.kt` (`findWithLockById`)
