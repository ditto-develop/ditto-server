# ADR 0015 — `chat_room`의 원본 식별자는 `(source_type, source_id)`로 접두어를 맞춘다

- 상태: Accepted (2026-08-04)
- 근거: 이름이 세 번 오간 이력(커밋 `50e888c`·`0225fd3`·`4196193`), 배포 장애 로그(2026-07-29), 결정(2026-08-04)

## Context

`chat_room`은 어떤 매칭에서 열린 방인지를 **두 컬럼 한 쌍**으로 가리킨다 — 유형(`PERSONAL`/`GROUP`, 앞으로 `REMATCH`)과 그 원본의 ID. 유일키 `chat_room_uk_1`도 이 쌍이고, `member_review.(match_type, match_id)`가 조인을 피하려고 같은 값을 복사해 둔다.

이 쌍의 앞 컬럼 이름이 **세 번 바뀌었다.**

| 시점 | 이름 | 비고 |
|---|---|---|
| 2026-07-15 `50e888c` | `room_type` | 채팅 최초 구현. **스쿼시로 버려져 main 이력에 없다** |
| 2026-07-26 `0225fd3`(PR #103) | `source_type` | main 최초 진입 |
| 2026-08-02 `4196193` | `room_type` | dev DB에 맞춤 (아래) |
| 2026-08-04 (이 결정) | `source_type` | 최종 |

세 번째 변경의 배경은 이름 논쟁이 아니라 **사고**였다. PR #103 머지 전에 누군가 브랜치의 7/15 버전 SQL(`room_type`)을 dev DB에 수동으로 실행해 뒀는데, main에는 이름이 바뀐 최종본(`source_type`)이 들어갔다. 마이그레이션이 자동 적용되지 않는 구조라 이 어긋남이 드러나지 않다가, 배포된 서버가 `Schema-validation: missing column [source_type]`으로 7/26~7/29 내내 크래시 루프에 빠졌다([ADR 0014](0014-ecs-metaspace-heap-rebalance.md)의 Context). 급히 코드를 DB에 맞춰 `room_type`으로 되돌려 기동시킨 것이 8/2 변경이다.

즉 현재 이름은 **장애 대응의 잔재**이지 선택의 결과가 아니다.

## Decision

`(source_type, source_id)`로 접두어를 맞춘다.

- 이 두 컬럼은 **원본을 가리키는 다형 FK 한 쌍**이다. `room_type`은 방을, `source_id`는 원본을 가리켜 같은 쌍의 두 값이 서로 다른 대상을 말하고 있었다. 쌍으로 읽히지 않으면 "무엇의 유형인가"를 매번 되짚어야 한다.
- 유일키·인덱스·복사본(`member_review`)이 모두 이 쌍을 단위로 다루므로, 이름도 한 단위로 보이는 편이 맞다.
- 엔티티 타입 이름 `ChatRoomType`은 그대로 둔다. 컬럼이 가리키는 대상(원본)과 값의 정의역(방 유형)은 다른 축이고, `member_review.matchType`도 이 enum을 재사용한다.

**적용은 rename 마이그레이션 한 방으로 하고 짧은 중단을 감수한다.** 컬럼이 하나뿐이라 구버전(`room_type`)과 신버전(`source_type`)이 공존할 수 없어, 롤링 배포와 호환되지 않는다. `ALTER` 실행 직후 곧바로 배포하며 그 사이 구버전은 기동하지 못한다.

## 검토했으나 채택하지 않은 대안

**① 현행 `room_type` 유지.** 변경 비용이 0이고 DB도 이미 그 상태다. 그러나 남는 이름이 사고 대응의 부산물이라 근거가 없고, `source_id`와 짝이 안 맞는 상태가 영구화된다. 앞으로 `REMATCH` 유형이 붙고 `I2`가 `(유형, 원본 ID)`로 방을 찾게 되면 어긋남이 더 눈에 띈다.

**② expand-contract**(`source_type` 추가 → 백필 → 배포 → `room_type` 제거). 무중단이지만 마이그레이션 2개와 배포 2회가 필요하고, 그 사이 두 컬럼이 공존해 "어느 쪽이 진짜인가"를 코드가 알아야 한다. 실사용자가 없는 지금 치를 값이 아니다. 출시 후 같은 일이 생기면 이쪽을 쓴다(선례: `quiz_set.week_started_on` 이관, `V20260726202324` → `V20260726204352`).

## Consequences

- 얻음: 쌍의 이름이 한 단위로 읽힌다. 이름이 오간 이력과 근거가 이 문서에 남아 **네 번째 변경을 막는다** — 앞으로 이 컬럼명을 바꾸려면 여기부터 반박해야 한다.
- 비용: **배포 중단이 필요하다.** `ALTER` 적용과 신버전 배포 사이에 구버전은 스키마 검증에 실패해 뜨지 못한다(7/29에 겪은 것과 같은 증상이며, 이번엔 의도된 짧은 구간이다). 운영 절차상 두 작업을 붙여서 수행해야 한다.
- 공개 API의 `ChatRoomResponse.roomType`이 `sourceType`으로 바뀐다. FE 레포에 이 필드 사용처가 없음을 확인했다(2026-08-03).
- 문서·ADR 다수가 이 쌍을 인용하고 있어 함께 갱신했다. 인용이 이렇게 퍼져 있다는 것 자체가, 다음에 또 바꾸면 같은 규모의 갱신이 반복된다는 뜻이다.

## Links

- commits: `50e888c`(최초 `room_type`, 버려짐) · `0225fd3`(PR #103, main 최초 `source_type`) · `4196193`(장애 대응으로 `room_type` 복귀)
- [ADR 0014](0014-ecs-metaspace-heap-rebalance.md) — 이 어긋남이 일으킨 크래시 루프가 Metaspace 문제를 가리고 있던 경위
- 핵심 파일: `domain/src/main/kotlin/com/ditto/domain/chat/entity/ChatRoom.kt`, `domain/db/V20260804003958_chat_room 컬럼명 통일 room_type to source_type.sql`
