# chat (채팅)

1:1(→그룹 확장 예정) 채팅. 매칭 성사 시 방 생성 → REST 조회(과거 페이징) + STOMP 실시간 송수신.

## 용어
- `ChatRoom` — 채팅방. `room_type`(PERSONAL/GROUP) + `source_id`(원본 매칭 ID). 매칭당 1개(unique).
- `ChatRoomMember` — 방 참여자 + `last_read_message_id`(회원별 읽음 커서).
- `ChatMessage` — 방 메시지. `id`(단조 증가)가 정렬·커서 페이징 키.
- destination — 전송 `/pub/chat/rooms/{roomId}`, 구독 `/sub/chat/rooms/{roomId}`.

## 핵심 규칙·불변식
- 방 생성: `PersonalMatch` 수락(ACCEPT) 시 같은 트랜잭션에서 생성, 멱등(이미 있으면 no-op). (그룹은 후속)
- 페이징: `id` 커서(`id < cursor` DESC), OFFSET 금지. 응답 `nextCursor` = 반환된 가장 과거 메시지 id(페이지가 가득 찼을 때만, 아니면 null).
- 읽음: 멤버별 `last_read_message_id` 단조 증가(뒤로 안 감). 안읽음 수 = `id > last_read` 카운트.
- 인가: 방 멤버만 조회·구독·전송 가능. STOMP 구독 인가는 `StompAuthChannelInterceptor`. WS 인증 배경: [ADR 0009](../adr/0009-websocket-stomp-auth.md).
- 전송 내용: trim 후 공백 불가, 최대 1000자(컬럼 상한).

## 실시간·스케일
- 전송: STOMP over WebSocket + 내장 SimpleBroker(인메모리, **단일 인스턴스 전제**). 백프레셔 하드닝(아웃바운드 큐 유한화, send/message 크기·시간 상한)으로 느린 소비자발 OOM 차단.
- 스케일아웃(2번째 레플리카) 시 SimpleBroker → 외부 STOMP relay(RabbitMQ 등). 인메모리 브로커는 레플리카를 못 넘는다(구독자·발행자가 서로 못 봄).

## 핵심 파일
- 도메인: `domain/.../chat/entity`(`ChatRoom`·`ChatRoomMember`·`ChatMessage`), `repository`(+`querydsl` 커서 페이징). 스키마: `domain/db/V20260715000000_채팅 테이블 추가.sql`.
- API: `api/.../chat/controller`(REST 조회), `service/ChatService`(방 생성·목록·메시지 페이징·읽음·전송), `websocket`(`WebSocketConfig`·`StompAuthChannelInterceptor`·`ChatStompController`).
