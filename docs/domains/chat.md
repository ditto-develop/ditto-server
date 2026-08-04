# chat (채팅)

1:1(→그룹 확장 예정) 채팅. 매칭 성사 시 방 생성 → REST 조회(과거 페이징) + STOMP 실시간 송수신.

## 용어
- `ChatRoom` — 채팅방. `source_type`(PERSONAL/GROUP) + `source_id`(원본 매칭 ID). 매칭당 1개(unique).
- `ChatRoomMember` — 방 참여자 + `last_read_message_id`(회원별 읽음 커서).
- `ChatMessage` — 방 메시지. `id`(단조 증가)가 정렬·커서 페이징 키.
- `ChatPeriod` — 채팅이 열려 있는 주말 구간(금 00:00 ~ 월 00:00). 값 객체.
- `ChatRoomStatus`(SCHEDULED/ACTIVE/ENDED), `ChatEndReason`(EXPIRED/USER_ENDED).
- destination — 전송 `/pub/chat/rooms/{roomId}`, 구독 `/sub/chat/rooms/{roomId}`.

## 생명주기
```text
SCHEDULED ──개방 시각 도달──> ACTIVE ──만료 또는 사용자 종료──> ENDED
```
- **채팅 기간은 금요일 00:00 개방 ~ 월요일 00:00 종료, 72시간이다.** 일반 매칭 채팅과 재매칭 채팅이 같은 창을 쓰므로 계산은 `ChatPeriod` 한 곳에만 둔다 — 두 곳에서 각자 계산하면 "그 주말이 언제인가"의 정의가 갈라진다.
- 주말이 시작된 뒤 만들어진 방은 다음 주로 미루지 않고 진행 중인 주말에 합류한다(생성 즉시 `ACTIVE`).
- `expires_at`은 **불변이 아니다** — 상호 동의 연장(#121)으로 뒤로 밀린다. 다만 값이 없는 방은 허용하지 않는다(NULL이면 만료 스케줄러가 못 잡아 영영 끝나지 않고, 그러면 평가도 열리지 않는다).
- `ACTIVE → ENDED`는 **한 번만 일어난다.** 엔티티 명령(`expire`·`endByUser`·`open`)은 조건이 맞을 때만 호출해야 하고, 이미 끝난 방을 다시 끝내려 하면 `check`로 막는다 — 조용히 넘기면 최초 종료 시각·사유가 덮여 "언제 왜 끝났는지"가 사라진다. 재요청을 성공으로 답하는 멱등 처리는 서비스가 `isEnded`를 먼저 보고 한다(불변식은 엔티티, 재시도 대응은 서비스).
- **상태를 바꾸는 모든 경로는 방 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 상태를 다시 확인하고 전이한다** — 사용자 종료뿐 아니라 만료 스케줄러·예약 개방도 마찬가지다. `check`는 프로세스 안에서만 유효해서 겹친 요청이 **둘 다 통과**하고, Hibernate 가 전 컬럼 UPDATE 를 날리므로 잠금 없이 읽은 스냅샷이 그 사이 커밋된 종료를 통째로 덮는다(끝난 방이 `ACTIVE`로 되살아난다). 그래서 스케줄러의 후보 조회는 엔티티가 아니라 **ID만** 가져온다(ADR 0011 의 재매칭과 같은 문제).
- 채팅 생명주기 시각(`opens_at`·`expires_at`)은 **어드민 시각 오버라이드를 따르지 않는다.** 저장되는 값인데 이를 판정하는 만료 스케줄러가 실제 시각으로 돌기 때문에, 생성만 가짜 시각을 쓰면 방이 만들어지자마자 만료되거나 며칠간 열리지 않고 오버라이드를 꺼도 그대로 남는다.
- 사용자 종료는 **두 사람만 있는 방에서만** 가능하다(`PERSONAL`·`REMATCH`). 그룹은 한 명이 나가도 남은 사람들의 대화가 이어져야 해 막는다(멤버 이탈은 별도 트랙). 그래서 검사는 "1:1인가"가 아니라 "그룹인가"로 둔다 — 두 사람 방이 늘어날 때 자동으로 허용된다.
- 사용자 종료는 그 요청이 **실제로 끝냈을 때만** 종료 SYSTEM 메시지를 발행하고 평가를 연다(`EndedChatReviewOpener`). 상대 화면이 즉시 바뀌어야 하는데 종료된 방은 재구독이 막혀 나중에 따라잡을 수 없다. 만료 마감은 클라이언트가 `expiresAt` 카운트다운으로 알 수 있어 발행하지 않는다.
- `end_reason`은 실제로 구분해서 쓰는 값만 둔다. 상대 차단을 동반한 종료(Figma `강제 종료` 버튼 = 상대 차단)와 그룹 인원 미달 자동 해체는 각 기능을 만들 때 값을 추가한다.
- **"누가 나갔는지"는 `chat_room`에 두지 않는다.** 나갈 때 남기는 `SYSTEM` 메시지의 `sender_id`가 그 사실을 들고 있고, 조회자가 자기 ID와 비교해 "상대방이 채팅을 종료했습니다"를 렌더링한다. 조회자에 따라 값이 달라지는 표현(`SELF_LEFT`/`PARTNER_LEFT`)을 저장하지 않으면서, 그룹의 멤버 이탈도 같은 메커니즘으로 커버된다.
- **종료된 방은 읽기 전용이다.** 전송·이미지 URL 발급·STOMP 구독은 `CHAT_ROOM_ENDED`(7004)로 막고, **조회와 읽음 처리는 허용한다** — 지난 대화와 평가 안내를 봐야 하기 때문이다. 그래서 접근 검증이 둘로 갈린다: 대화를 이어가는 경로는 `ChatRoomAccessChecker.validateActiveMember`, 읽기만 하는 경로는 `validateMember`.

## SYSTEM 메시지 (FE 계약)

`messageType = SYSTEM`인 메시지의 `content`에는 **완성된 문장이 아니라 사건 코드**가 들어간다. 같은 사건이라도 보는 사람에 따라 문구가 달라지기 때문이다 — 1:1에서 나간 본인에게 "상대방이 채팅을 종료했습니다"는 거짓이고, 그룹은 "○○님이 나갔습니다"처럼 닉네임을 붙여야 한다. 표시 문구는 `senderId`와 방 유형을 아는 클라이언트가 만들며, 그래서 문구·닉네임이 바뀌어도 과거 메시지가 낡지 않는다.

| 코드 | 사건 | `senderId` |
|---|---|---|
| `USER_LEFT` | 참여자가 채팅을 종료함 | 나간 회원 |

값 추가 전용이다 — 그룹 인원 미달 해체, 채팅 연장([#121](https://github.com/ditto-develop/ditto-server/issues/121)) 등은 해당 기능을 만들 때 코드를 추가한다.

## 핵심 규칙·불변식
- 방 생성: `PersonalMatch` 수락(ACCEPT) 시, 그룹은 `GroupMatch` 활성화 시 같은 트랜잭션에서 생성, 멱등(이미 있으면 no-op).
- 페이징: `id` 커서(`id < cursor` DESC), OFFSET 금지. 응답 `nextCursor` = 반환된 가장 과거 메시지 id(페이지가 가득 찼을 때만, 아니면 null).
- 읽음: 멤버별 `last_read_message_id` 단조 증가(뒤로 안 감). 안읽음 수 = `id > last_read` 카운트.
- 인가: 방 멤버만 조회·구독·전송 가능. STOMP 구독 인가는 `StompAuthChannelInterceptor`. WS 인증 배경: [ADR 0009](../adr/0009-websocket-stomp-auth.md).
- 전송 내용: trim 후 공백 불가, 최대 1000자(컬럼 상한).

## 실시간·스케일
- 전송: STOMP over WebSocket + 내장 SimpleBroker(인메모리, **단일 인스턴스 전제**). 백프레셔 하드닝(아웃바운드 큐 유한화, send/message 크기·시간 상한)으로 느린 소비자발 OOM 차단.
- 스케일아웃(2번째 레플리카) 시 SimpleBroker → 외부 STOMP relay(RabbitMQ 등). 인메모리 브로커는 레플리카를 못 넘는다(구독자·발행자가 서로 못 봄).

## 핵심 파일
- 도메인: `domain/.../chat/entity`(`ChatRoom`·`ChatRoomMember`·`ChatMessage`·`ChatPeriod`), `repository`(+`querydsl` 커서 페이징). 스키마: `domain/db/V20260715000000_채팅 테이블 추가.sql`, 생명주기 컬럼은 `V20260803223317_채팅 종료 생명주기 컬럼 추가.sql`.
- API: `api/.../chat/controller`(REST 조회·종료), `service/ChatService`(방 생성·목록·메시지 페이징·읽음·전송), `service/ChatRoomEndService`(만료 마감·예약 개방·사용자 종료), `service/ChatRoomAccessChecker`(멤버십·종료 여부 판정), `scheduler/ChatRoomLifecycleScheduler`(개방·마감 + 평가 열기·누락 복구), `websocket`(`WebSocketConfig`·`StompAuthChannelInterceptor`·`ChatStompController`).
