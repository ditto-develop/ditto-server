# ADR 0016 — 같은 쌍의 재매칭 채팅방이 둘 열리는 것을 허용한다

- 상태: Accepted (2026-08-04)
- 근거: discussion(2026-08-04, `I2` 구현 중 발견), [ADR 0013](0013-rematch-duplicate-at-room-creation.md) 재검토

## Context

[ADR 0013](0013-rematch-duplicate-at-room-creation.md)은 같은 두 사람이 한 주에 서로 다른 그룹에서 만나 양쪽 모두 서로를 선택했을 때, `rematch` 행은 둘 다 `MATCHED`로 두고 **방을 만드는 시점(`I2`)에서 하나만 만들도록** 정했다. 두 사람이 서로 다른 방에 말을 걸어 상대가 응답하지 않는다고 오해하는 것을 막으려는 결정이었다.

`I2`를 구현하면서 그 방식에 구멍이 드러났다. 방 예약은 "성사됐는데 방이 없는 쌍"을 anti-join으로 찾는다 — 방 자체가 처리 완료 기록이므로 별도 표시가 필요 없다는 것이 설계의 핵심이었다. 그런데 중복으로 **건너뛴 `rematch` 행에는 그 기록이 영원히 생기지 않는다.** 조회 조건(`MATCHED` + 방 없음)을 계속 만족하므로 매 주기 다시 잡히고, `ORDER BY matched_at ASC`라 시간이 갈수록 배치 앞자리에 고정된다. 이런 행이 배치 크기만큼 쌓이면 그 뒤에 성사된 쌍이 방을 얻지 못한다.

조회 조건으로 걸러 잔류를 없애려면 "이 성사가 어느 주말로 가는가"가 SQL에 있어야 한다. `ChatPeriod`는 `previousOrSame(MONDAY) + 4일`로 계산하는데 MySQL(`WEEKDAY`)과 H2(`DAY_OF_WEEK`)의 함수가 갈려 운영과 테스트가 같은 식을 쓸 수 없다. `rematch.week_started_on`으로 대신할 수도 없다 — 그것은 **원본 그룹의 주**이고 성사는 그 채팅이 끝난 뒤에 일어나며, 조기 종료 시에는 같은 주말에 합류해 매핑이 1:1이 아니다.

남은 선택은 셋이었다. ① 잔류를 감수하고 감시한다. ② `rematch`에 처리 표시 컬럼을 두어 근본 해결한다(ADR 0013의 "새 컬럼 없이"와 [ADR 0012](0012-rematch-as-separate-table.md)의 "방 정보 비저장"을 건드린다). ③ 중복 자체를 허용한다.

## Decision

**중복을 허용한다.** `rematch` 행마다 방을 하나씩 만들고, 같은 쌍·같은 주말이어도 막지 않는다. ADR 0013의 결정을 대체한다.

방 예약은 `MATCHED`인 모든 쌍을 예약하므로 **조회가 항상 해소된다** — 잔류 행이 생기지 않고, 처리 표시 컬럼도 필요 없다. `chat_room.(source_type, source_id)` = (`REMATCH`, `rematch.id`)가 모든 성사에 대해 성립하므로 `A3`도 이 튜플로 방을 찾을 수 있다.

**결정적인 이유는 현재 구조에서 그 중복이 발생할 수 없다는 것이다.** 중복이 생기려면 같은 두 사람이 한 주에 서로 다른 두 그룹에서 만나야 하는데, `GroupMatchService`가 참여 시 `existsByMemberIdAndQuizSetId`로 막아 **한 회원은 퀴즈셋당 그룹 하나에만 참여한다.** 한 주에 그룹 퀴즈셋이 하나뿐인 현재 운영에서는 그 주의 그룹도 하나이므로 같은 쌍이 두 그룹에서 만날 경로가 없다. ADR 0013은 [ADR 0008](0008-matching-entity-uniqueness-modeling.md)이 "퀴즈셋당 여러 그룹"을 허용한다는 점만 보고 이 가드를 함께 세지 않았다.

그래서 이 결정은 "중복 UX 를 감수한다"가 아니라 **"일어나지 않는 일에 방어 코드를 두지 않는다"**에 가깝다. 실제로 발생할 수 있게 되는 시점은 한 주에 그룹 퀴즈셋을 여러 개 운영하기로 할 때이며, 그때 다시 판단한다.

## Consequences

- 얻음: 방 예약 조회에 잔류 행이 생기지 않는다. 같은 함정(결과물 없음을 미처리 신호로 쓰는 구조)이 평가 트랙에서 이미 두 번 나왔고([#130](https://github.com/ditto-develop/ditto-server/issues/130), 재매칭 방의 평가 제외), 여기서는 그 구조 자체를 피한다.
- 얻음: 중복 판정 코드가 없다. 조회·조립·`A3` 어디에도 "같은 쌍" 규칙이 퍼지지 않는다.
- 얻음: `A3`가 `(REMATCH, rematch.id)`로 방을 찾는다. ADR 0013을 따랐다면 건너뛴 성사에는 그 튜플의 방이 없어 쌍 기준 조회가 필요했고, [rematch 도메인](../domains/rematch.md)의 "방은 이 튜플로 찾는다"는 서술도 깨졌다.
- 비용: 한 주에 그룹 퀴즈셋을 여러 개 운영하게 되면 같은 사람과 방이 둘 열릴 수 있다. 방 목록에 같은 상대가 두 번 보이고, 각자 다른 방에 말을 걸면 응답이 없는 것처럼 보인다. **이 결정은 그 운영 조건에 묶여 있다** — 퀴즈셋을 주당 여러 개로 늘리는 변경은 이 ADR을 함께 검토해야 한다.
- 후속: 그때 ② (`rematch`에 처리 표시 컬럼)로 승격한다. 중복 방지와 잔류 방지를 함께 얻으며, 표시 컬럼은 insert 이후 갱신만 하므로 과거 데이터 백필이 필요 없다.
- 테스트로 고정하지 않는다. 발생 경로가 없는 동작을 테스트로 박으면 "이렇게 동작해야 한다"로 읽혀, 나중에 막기로 할 때 그 테스트가 근거처럼 남는다.

## Links

- [ADR 0013](0013-rematch-duplicate-at-room-creation.md) — 이 결정으로 대체됨(중복을 방 생성 시점에서 막는다)
- [ADR 0012](0012-rematch-as-separate-table.md) — `rematch`가 채팅 시각·방 ID를 저장하지 않는다(② 를 택하지 않은 이유의 한 축)
- [ADR 0008](0008-matching-entity-uniqueness-modeling.md) — 한 멤버의 다중 그룹 참여 허용(중복의 근원)
- 핵심 파일: `api/src/main/kotlin/com/ditto/api/rematch/service/RematchChatRoomOpener.kt`(예약), `domain/src/main/kotlin/com/ditto/domain/rematch/repository/querydsl/RematchRepositoryImpl.kt`(예약 대상 조회)
