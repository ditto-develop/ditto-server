# ADR 0026 — 매칭 배치는 퀴즈셋마다 별도 트랜잭션으로 돈다

- 상태: Accepted (2026-09-15)
- 근거: #177 리뷰 지적 (어드민 매칭 재생성 결과 성공/실패 분리)

## Context

`MatchmakingService.runScheduledMatching`은 마감됐고 후보가 없는 퀴즈셋을 anti-join 으로 골라 한 트랜잭션 안에서
차례로 `generateMatchingCandidates`를 돌렸다. 그동안 항목 하나가 처리 불가여도(응답이 시작된 그룹 셋)
`GroupCandidateWriter`가 조용히 `return` 해서 배치가 실패할 일이 없었다.

#177 에서 그 건너뜀을 `WarnException(MATCH_CANDIDATES_ALREADY_RESPONDED)`으로 바꿨다 — 어드민이 재생성을
눌렀는데 성공처럼 보이면 안 되기 때문이다. 그러자 배치 쪽에 새 실패 경로가 생겼다. anti-join 이 `group_match` 행이
있는 셋을 제외하므로 평소엔 도달하지 않지만, 대상 선정 직후 다른 어드민이 같은 셋을 재생성하고 누군가 바로 수락하면
배치가 그 셋을 처리할 때 예외가 나고, **같은 트랜잭션에 있던 다른 셋의 후보까지 전부 롤백**된다.

단순히 루프 안에서 예외를 잡는 방법은 쓸 수 없다. `GroupCandidateWriter.replace`가 `@Transactional` 프록시라
예외가 그 경계를 지나며 참여 트랜잭션을 rollback-only 로 표시하고, 잡아서 계속 진행해도 커밋 시점에
`UnexpectedRollbackException`으로 통째로 실패한다.

## Decision

- 배치 루프를 `MatchmakingService`에서 떼어 **트랜잭션이 없는 `MatchingBatchFacade`** 로 옮긴다. 같은 클래스 안에서
  `generateMatchingCandidates`를 부르면 자기 호출이라 프록시를 거치지 않아 트랜잭션 경계를 만들 수 없다.
- `MatchmakingService.generateMatchingCandidates`는 그대로 `@Transactional`(REQUIRED)이다. facade 가 트랜잭션 없이
  프록시로 부르므로 그것만으로 퀴즈셋 하나가 **자기 트랜잭션**을 갖는다. `REQUIRES_NEW`로 강제하는 안은 버렸다 —
  어드민 단건 재생성과 `@Transactional` 통합 테스트(`AdminWebTest`)가 같은 메서드를 바깥 트랜잭션 안에서 쓰는데,
  새 트랜잭션은 바깥의 미커밋 데이터를 보지 못해 NOT_FOUND 가 난다. 대신 "facade 와 그 호출자는 트랜잭션을 열지 않는다"를
  불변식으로 문서화한다.
- facade 는 실패를 `runCatchingExceptions`로 받아 경고 로그만 남기고 다음 셋으로 넘어간다. 반환값(알림 대상 ID)은
  **성공한 셋만** 담는다. 실패한 셋은 후보가 없는 채로 남아 다음 배치의 anti-join 이 다시 집는다.
- `SanctionExpiryService.expireDue`는 자기 트랜잭션에서 먼저 커밋된다 — "원복된 회원이 이번 매칭 대상에 포함된다"는
  ADR 0009 의 의도는 그대로다.
- `TransactionTemplate`+`NOT_SUPPORTED` 로 같은 클래스 안에서 해결하는 안도 검토했지만, 트랜잭션 경계가 어노테이션만으로
  읽히지 않고 클래스 레벨 `readOnly` 를 메서드마다 끄는 식이라 facade 분리를 택했다.

## Consequences

- 배치의 원자성 단위가 "전체"에서 "퀴즈셋"으로 줄어든다. 셋끼리는 독립이라 잃는 것이 없고, 한 셋의 문제가 다른 셋의
  주간 매칭을 날리는 일이 사라진다.
- 실패 격리 분기는 자연 데이터로 재현할 수 없다(anti-join 이 막는다). 통합 테스트는 성공 경로·멱등만 덮고, 격리는 코드
  리뷰로 지킨다.
- 단일 셋을 다루는 어드민 재생성 경로(`AdminMatchController`, `MatchAdminController`)는 그대로 예외를 올려 실패로 보인다.

## Links

- `docs/domains/match.md` 불변식 "스케줄러 배치는 퀴즈셋마다 별도 트랜잭션"
- ADR 0009 (제재 만료 원복을 후보 생성보다 먼저)
