# ADR 0057 — 1:1 대화방은 수락하는 즉시 연다(목요일 포함)

- 상태: Accepted (2026-11-26)
- 근거: 이슈 #399

## Context

1:1 대화방은 서로 수락해도 그 주 금요일 00:00에 열렸다. 목요일(매칭 기간)에 수락하면 방이 `SCHEDULED`로 하루를 기다렸다. 주말에 수락하면 진행 중인 주말에 합류해 바로 열리므로, 기다리는 경우는 목요일 수락뿐이다.

## Decision

- 1:1(`PERSONAL`) 방은 만들 때 개방 시각을 만든 시각으로 당긴다(`ChatPeriod.openedBy`, `ChatRoom.personal`). 닫는 시각은 그대로 그 주 월 00:00이라 목요일 수락이면 대화가 최대 약 4일이다.
- 배포 전 목요일에 수락돼 `SCHEDULED`로 남은 1:1 방은 생명주기 스케줄러가 다음 주기에 연다. 개방 후보 조회가 1:1 은 개방 시각과 관계없이 집고(`findAllIdsDueToOpen`), `ChatRoom.open`이 개방 시각을 연 시각으로 당긴다. 응답의 `opensAt`이 실제로 열린 시각과 맞아야 해서다. 이 경로로 열린 방은 채팅방 오픈 알림을 받는다.
- 그룹·재매칭 방은 그대로 금요일에 연다. 그룹은 응답 마감(금 00:00)과 개방이 같은 순간이라는 전제([ADR 0056](0056-group-invitation-default-join.md))가 있고, 재매칭은 온전한 72시간을 주는 규칙이 있다.
- 대화 신청·수락 알림은 요일·시크릿 매칭 플래그와 관계없이 "바로 열려요" 문구를 쓴다(#391 조건 제거).
- 기능 플래그는 두지 않는다.

## Consequences

- 얻음: 목요일에 서로 수락한 두 사람이 바로 대화한다.
- 바뀜: 1:1 대화 기간이 "금~일 72시간" 고정이 아니다(목요일 수락이면 최대 약 96시간).
- 바뀜: 첫 메시지 리마인드는 개방 시각 + 12시간 기준이라, 목요일 낮 이후 수락한 방은 금요일 새벽에 나갈 수 있다. 주말 수락 방도 원래 그랬고 알림 금지 시간대는 없다.
- 남은 일: QA 바로가기 `CHAT_FIRST_MESSAGE_NOTIFIED`(금 12:01)는 목요일에 연 방을 집지 못한다. 어드민 통계의 "첫째~셋째 날, 72시간 뒤" 구간과 "금요일 00:00에 열립니다" 문구도 목요일 방을 고려하지 않는다.
- 바뀜: `opens_at`이 불변이 아니다. #399 이전 예약 방을 열 때만 바뀐다.
- QA: 오버라이드를 켠 동안 수락한 1:1 방은 오버라이드 시각에 열린다. 끄면 그 방의 `opens_at`이 실제 시각보다 미래일 수 있다(상태는 이미 `ACTIVE`).

## Links

- 핵심 파일: `domain/.../chat/entity/ChatPeriod.kt`(`openedBy`), `domain/.../chat/entity/ChatRoom.kt`(`personal`·`open`·`opensOnAcceptance`), `domain/.../chat/repository/querydsl/ChatRoomRepositoryImpl.kt`(`findAllIdsDueToOpen`), `api/.../notification/notifier/PersonalMatchNotifier.kt`
