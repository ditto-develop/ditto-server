# chat (채팅)

1:1(→그룹 확장 예정) 채팅. 매칭 성사 시 방 생성 → REST 조회(과거 페이징) + STOMP 실시간 송수신.

## 용어
- `ChatRoom` — 채팅방. `source_type`(PERSONAL/GROUP) + `source_id`(원본 매칭 ID). 매칭당 1개(unique).
- `ChatRoomMember` — 방 참여자 + `last_read_message_id`(회원별 읽음 커서) + `left_at`(이탈 시각, 참여 중이면 NULL).
- `ChatMessage` — 방 메시지. `id`(단조 증가)가 정렬·커서 페이징 키.
- `ChatPeriod` — 채팅이 열려 있는 주말 구간(금 00:00 ~ 월 00:00). 값 객체.
- `ChatRoomStatus`(SCHEDULED/ACTIVE/ENDED), `ChatEndReason`(EXPIRED/USER_ENDED/INSUFFICIENT_MEMBERS).
- destination — 전송 `/pub/chat/rooms/{roomId}`, 구독 `/sub/chat/rooms/{roomId}`.

## 생명주기
```text
SCHEDULED ──개방 시각 도달──> ACTIVE ──만료 또는 사용자 종료──> ENDED
    └────────── 열리지 못한 채 기한이 지남 / 사용자 종료 ──────────┘
```
- **채팅 기간은 금요일 00:00 개방 ~ 월요일 00:00 종료, 72시간이다.** 일반 매칭 채팅과 재매칭 채팅이 같은 창을 쓰므로 계산은 `ChatPeriod` 한 곳에만 둔다 — 두 곳에서 각자 계산하면 "그 주말이 언제인가"의 정의가 갈라진다.
- **일반 매칭 방**은 주말이 시작된 뒤 만들어져도 다음 주로 미루지 않고 진행 중인 주말에 합류한다(`weekendOf`, 생성 즉시 `ACTIVE`) — 수락 즉시 대화할 수 있어야 한다.
- **재매칭 방**은 합류시키지 않고 **성사 이후 처음 오는 금요일**에 연다(`upcomingWeekendFrom`). 기획이 "금요일 00:00 채팅방 오픈"만 정해 특정 주말을 약속하지 않으므로, 남은 몇 시간에 급히 열지 않고 온전한 72시간을 준다. 배경은 [rematch 도메인](rematch.md).
- **이미 닫힌 창으로는 방을 만들지 않는다.** 만들면 개방 시각이 지났으므로 `ACTIVE`로 태어난 뒤 만료 스케줄러가 곧바로 끝낸다. `weekendOf`는 과거 시각으로 부르면 닫힌 구간을 돌려주므로, 방을 만드는 쪽은 만드는 시점으로 부르거나 `upcomingWeekendFrom`을 쓴다.
- `expires_at`은 **불변이 아니다** — 상호 동의 연장(#121)으로 뒤로 밀린다. 다만 값이 없는 방은 허용하지 않는다(NULL이면 만료 스케줄러가 못 잡아 영영 끝나지 않고, 그러면 평가도 열리지 않는다).
- `ACTIVE → ENDED`는 **한 번만 일어난다.** 엔티티 명령(`expire`·`endByUser`·`open`)은 조건이 맞을 때만 호출해야 하고, 이미 끝난 방을 다시 끝내려 하면 `check`로 막는다 — 조용히 넘기면 최초 종료 시각·사유가 덮여 "언제 왜 끝났는지"가 사라진다. 재요청을 성공으로 답하는 멱등 처리는 서비스가 `isEnded`를 먼저 보고 한다(불변식은 엔티티, 재시도 대응은 서비스).
- **상태를 바꾸는 모든 경로는 방 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 상태를 다시 확인하고 전이한다** — 사용자 종료뿐 아니라 만료 스케줄러·예약 개방도 마찬가지다. `check`는 프로세스 안에서만 유효해서 겹친 요청이 **둘 다 통과**하고, Hibernate 가 전 컬럼 UPDATE 를 날리므로 잠금 없이 읽은 스냅샷이 그 사이 커밋된 종료를 통째로 덮는다(끝난 방이 `ACTIVE`로 되살아난다). 그래서 스케줄러의 후보 조회는 엔티티가 아니라 **ID만** 가져온다(ADR 0011 의 재매칭과 같은 문제).
- **어드민 시각 오버라이드는 저장이 아니라 판단에만 적용한다**(#146). 생명주기 시각(`opens_at`·`expires_at`)을 **저장**할 때는 실제 시각을 쓴다 — 가짜 시각이 박히면 방이 만들어지자마자 만료되거나 미래에 갇히고, 오버라이드를 꺼도 그대로 남는다. 반면 상태 전이를 **판단**하는 스케줄러(개방·마감·종료 임박 알림)는 오버라이드를 따른다. 판단은 `status`만 바꿔서 저장값을 더럽히지 않고, 접근 검사가 시각이 아니라 `status`를 보므로 여기가 실제 시각을 쓰면 시각을 채팅 요일로 맞춰도 방이 열리지 않는다.
- 그 대가로 **오버라이드를 `expires_at` 이후로 옮기면 그 방들이 마감되고 되돌릴 수 없다.** 시각 오버라이드로 종료·평가 개방 흐름을 확인하려면 감수해야 한다.
- 사용자 종료는 **두 사람만 있는 방에서만** 가능하다(`PERSONAL`·`REMATCH`). 그룹은 한 명이 나가도 남은 사람들의 대화가 이어져야 해 막는다. 그래서 검사는 "1:1인가"가 아니라 "그룹인가"로 둔다 — 두 사람 방이 늘어날 때 자동으로 허용된다.
- **그룹은 개별 이탈(`POST /leave`)로 나간다.** 이탈은 행 삭제가 아니라 `chat_room_member.left_at` 소프트 컬럼이다 — 행을 지우면 읽음 커서·`matchCount`·과거 SYSTEM 메시지의 `senderId` 해석 근거가 사라진다. **잔여 2명까지는 방을 유지**하고 1명만 남는 순간 해체한다(`INSUFFICIENT_MEMBERS`). 두 사람 방의 leave 는 종료와 같은 뜻이라 `end`와 같은 전이를 탄다. 동시 이탈 경합은 방 행 잠금으로 직렬화한다 — 멤버 행을 바꾸지만 잠그는 대상이 방 행인 이유는 "잔여 인원 카운트 + 해체 판정"이 방 단위의 원자적 결정이기 때문이다.
- 사용자 종료는 그 요청이 **실제로 끝냈을 때만** 종료 SYSTEM 메시지를 발행하고 평가를 연다(`EndedChatReviewOpener`). 상대 화면이 즉시 바뀌어야 하는데 종료된 방은 재구독이 막혀 나중에 따라잡을 수 없다. 만료 마감은 클라이언트가 `expiresAt` 카운트다운으로 알 수 있어 발행하지 않는다.
- `end_reason`은 실제로 구분해서 쓰는 값만 둔다(`EXPIRED`·`USER_ENDED`·`INSUFFICIENT_MEMBERS`). 상대 차단을 동반한 종료(Figma `강제 종료` 버튼 = 상대 차단)는 차단 기능을 만들 때 값을 추가한다.
- **"누가 나갔는지"는 `chat_room`에 두지 않는다.** 나갈 때 남기는 `SYSTEM` 메시지의 `sender_id`가 그 사실을 들고 있고, 조회자가 자기 ID와 비교해 "상대방이 채팅을 종료했습니다"를 렌더링한다. 조회자에 따라 값이 달라지는 표현(`SELF_LEFT`/`PARTNER_LEFT`)을 저장하지 않으면서, 그룹의 멤버 이탈도 같은 메커니즘으로 커버된다.
- **개방 전 방도 읽기 전용이다.** 전송·이미지 URL 발급·STOMP 구독은 `CHAT_ROOM_NOT_OPENED`(7005)로 막는다 — 매칭 수락은 주중에도 일어나 방이 며칠간 `SCHEDULED`로 존재하는데, 그 사이 대화가 오가면 "금~일 72시간"이 클라이언트 렌더링 규칙에 불과해진다. 방 목록·메시지 조회·읽음은 허용해 "금요일에 열려요"를 보여줄 수 있게 한다. **종료 후와 다른 코드를 쓰는 이유**는 클라이언트가 두 상태를 가려 보여줘야 하기 때문이다.
- **종료된 방은 읽기 전용이다.** 전송·이미지 URL 발급·STOMP 구독은 `CHAT_ROOM_ENDED`(7004)로 막고, **조회와 읽음 처리는 허용한다** — 지난 대화와 평가 안내를 봐야 하기 때문이다. 그래서 접근 검증이 둘로 갈린다: 대화를 이어가는 경로는 `ChatRoomAccessChecker.validateActiveMember`, 읽기만 하는 경로는 `validateMember`.

## SYSTEM 메시지 (FE 계약)

`messageType = SYSTEM`인 메시지의 `content`에는 **완성된 문장이 아니라 사건 코드**가 들어간다. 같은 사건이라도 보는 사람에 따라 문구가 달라지기 때문이다 — 1:1에서 나간 본인에게 "상대방이 채팅을 종료했습니다"는 거짓이고, 그룹은 "○○님이 나갔습니다"처럼 닉네임을 붙여야 한다. 표시 문구는 `senderId`와 방 유형을 아는 클라이언트가 만들며, 그래서 문구·닉네임이 바뀌어도 과거 메시지가 낡지 않는다.

| 코드 | 사건 | `senderId` | 방 |
|---|---|---|---|
| `USER_LEFT` | 참여자가 채팅을 종료함 (두 사람 방) | 나간 회원 | 끝남 |
| `MEMBER_LEFT` | 그룹 멤버가 방을 나감 | 나간 회원 | **계속됨** |
| `INSUFFICIENT_MEMBERS` | 이탈로 잔여 1명이 되어 자동 해체됨 | 마지막 이탈자 | 끝남 |
| `VOTE_CREATED:{voteId}` | 만남 투표가 만들어짐 | 만든 회원 | 계속됨 |
| `VOTE_CLOSED:{voteId}` | 만남 투표가 마감됨 | 마감한 회원 | 계속됨 |

`USER_LEFT`와 `MEMBER_LEFT`를 나누는 이유: 재사용하면 같은 코드가 1:1에서는 "방이 끝났다", 그룹에서는 "방은 계속된다"로 정반대를 뜻해 과거 메시지를 되짚어 해석할 수 없어진다. 해체 시에는 `MEMBER_LEFT` + `INSUFFICIENT_MEMBERS`가 연달아 발행된다.

투표 코드만 `코드:voteId` 접미가 붙는다 — 클라이언트가 배너·카드에서 상세를 재조회하려면 voteId 가 필요해서다(콜론 split 한 번).

값 추가 전용이다 — 채팅 연장([#121](https://github.com/ditto-develop/ditto-server/issues/121)) 등은 해당 기능을 만들 때 코드를 추가한다.

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
