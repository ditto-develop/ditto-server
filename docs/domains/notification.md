# notification (알림)

알림 센터(피그마 `7.2 알림 센터`). 사건이 생길 때 **행을 적재**하고, 화면이 최신순 커서 페이징으로 읽고 읽음 표시한다.

푸시 발송(FCM)은 아직 없다. 이 도메인은 인앱 기록만 다루며, 발송이 붙으면 같은 적재 지점에서 함께 보낸다.

## 용어

- `Notification` — 알림 한 행. **수신자 1명당 1행**이다(같은 사건이라도 받는 사람마다 문구가 다르고 읽음도 따로다).
- `NotificationType` — 알림 유형. 카테고리·`target_id`의 대상·중복 정책을 이 enum이 정한다.
- `NotificationCategory` — 화면 필터 칩(`MATCHING`/`CHAT`/`SYSTEM`). **컬럼이 아니라 유형에서 파생**된다. "전체" 칩은 값이 아니라 필터 없음이다.
- `DuplicatePolicy` — 같은 대상에 다시 발생했을 때의 처리(`ALLOW`/`ONCE_PER_TARGET`/`COLLAPSE_UNREAD`).
- `NotificationAppender` — 알림을 남기는 유일한 입구. 실패를 삼킨다.
- `NotificationWriter` — 실제 저장. `REQUIRES_NEW`로 자기 트랜잭션에서 커밋한다.
- `NotificationMessages` — 문구 한곳 모음(정본은 기획의 "알림 문구 정책" 문서).

## 유형 표 (FE 계약)

| 유형 | 카테고리 | `target_id` | 중복 정책 | 적재 지점 |
|---|---|---|---|---|
| `MATCH_RESULT` | MATCHING | `quiz_set.id` | 대상당 1회 | `MatchingScheduler` → `MatchResultNotifier` |
| `GROUP_FORMED` | MATCHING | `chat_room.id`(그룹) | 대상당 1회 | `GroupMatchService.joinGroupMatch` |
| `REMATCH_MATCHED` | MATCHING | `chat_room.id`(재매칭) | 대상당 1회 | `RematchChatRoomOpener.reserve` |
| `REVIEW_REQUEST` | MATCHING | `chat_room.id`(끝난 방) | 대상당 1회 | `ChatRoomLifecycleScheduler`·`ChatController.end` → `ReviewRequestNotifier` |
| `CHAT_MESSAGE` | CHAT | `chat_room.id` | 안읽은 것 접기 | `ChatStompController` → `ChatMessageNotifier` |
| `CHAT_ENDING_SOON` | CHAT | `chat_room.id` | 대상당 1회 | `ChatRoomLifecycleScheduler` → `ChatEndingSoonNotifier` |
| `SYSTEM_NOTICE` | SYSTEM | 없음 | 제한 없음 | **발송 주체 없음**(어드민 공지 화면 후속) |

`MATCH_RESULT`의 대상이 퀴즈셋인 것은 화면 이동용이 아니라 **"주마다 한 번"의 판정 기준**이다. 회원+유형만으로 막으면 평생 한 번만 알린다.

## 불변식

- **문구는 발송 시점에 확정해 저장한다.** 조회 때 다시 렌더하지 않는다 — 곧 붙을 푸시와 센터가 같은 문구여야 하고, 센터는 "그때 무엇을 알렸는가"의 기록이라 닉네임이 바뀐 뒤 다시 렌더하면 사실이 달라진다. 채팅 SYSTEM 메시지와 **반대 방향**의 선택이다([ADR 0018](../adr/0018-notification-center-append-and-read.md)).
- **`id` 정렬 = 시간 정렬.** 커서 페이징이 `id` 하나만 쓰므로, 알림을 접을 때 기존 행을 갱신하지 않고 **지우고 다시 삽입**한다. 이 불변식을 깨면 커서가 두 키를 다뤄야 한다.
- **안읽음은 `read_at == null` 이다.** 채팅처럼 읽음 커서 하나로 접지 않는다 — 화면이 개별 읽음을 요구한다.
- **보관·조회 창은 30일이다**(`Notification.RETENTION_DAYS`). 조회가 그 밖을 자르고, 같은 기준으로 purge 배치가 지운다. 미읽음 수(배지)도 같은 창을 써야 한다 — 창이 어긋나면 배지가 0이 되지 않는다.
- **설정의 알림 토글은 이 목록을 막지 않는다.** 토글(`member_notification_setting`)은 푸시 수신 동의이고 센터는 인앱 기록이다. 채팅 알림을 끈 사람도 센터에서는 새 메시지를 본다.
- **적재는 비즈니스 트랜잭션을 되돌리지 않는다.** `REQUIRES_NEW` + 실패 흡수. 반대급부로 롤백된 작업의 알림이 드물게 남을 수 있고, 그건 감수한다.
- **알림 행이 곧 처리 완료 표시다.** "대상당 1회" 유형은 존재 검사로 막으므로, 스케줄러가 같은 방·같은 퀴즈셋을 매 주기 다시 집어와도 알림은 하나다. 별도 플래그나 outbox 가 없다(`RematchChatRoomOpener`·`EndedChatReviewOpener`와 같은 수렴 루프).
- **재매칭 방 종료에는 평가 요청을 알리지 않는다.** 재매칭 채팅은 평가를 열지 않기 때문이다(#132). `ReviewRequestNotifier`가 `REMATCH`를 걸러낸다.
- **탈퇴 완전 삭제는 알림도 지운다.** 본문에 닉네임·메시지 미리보기(개인정보)가 들어 있다.

## 적재 지점을 어디에 두는가

원칙은 **커밋된 뒤에, 사건을 아는 곳에서**다.

- 스케줄러가 부르는 경로(`MATCH_RESULT`·`REVIEW_REQUEST`·`CHAT_ENDING_SOON`)는 전이가 커밋된 뒤에 부르므로 롤백된 작업의 알림이 남지 않는다.
- 트랜잭션 안에서 부르는 경로(`GROUP_FORMED`)는 그 사실을 아는 곳이 거기뿐이라 남겨 뒀다. 롤백 시 알림만 남을 수 있다는 것을 알고 택했다.
- 실시간 경로(`CHAT_MESSAGE`)는 **브로드캐스트 뒤에** 둔다 — 전달이 적재를 기다리지 않아야 한다.

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/notifications` | 최신순 커서 페이징. `category`·`cursor`·`size`(기본 20, 최대 100) |
| GET | `/api/v1/notifications/unread-count` | 홈 헤더 벨 배지용 |
| PUT | `/api/v1/notifications/{id}/read` | 개별 읽음(멱등). 남의 알림은 404 |
| PUT | `/api/v1/notifications/read-all` | 전체 읽음. `readCount` 반환 |

응답 필드와 예시는 `/docs`(REST Docs → Swagger UI). FE 연동 가이드는 레포 위키의 "알림 센터 API 연동 가이드".

## 시각

**실제 시각으로 동작한다** — 어드민 시각 오버라이드(`ServerTimeProvider`)를 쓰지 않는다. `created_at`을 JPA Auditing 이 실제 시각으로 채우므로, 조회 창을 가짜 시각으로 계산하면 오버라이드가 미래일 때 방금 온 알림이 창 밖으로 밀려 사라진다.

## TODO (미확정)

- 푸시 발송(FCM) — 디바이스 토큰 저장, 알림 토글을 게이트로 사용
- 실시간 배지 — 현재는 폴링/재조회. STOMP 개인 큐 여부 미정
- `SYSTEM_NOTICE` 발송 주체 — 어드민 공지 화면
- 투표 알림(`투표 결과가 확정됐어요`) — 만남 투표 기능 자체가 없다
- 채팅 연장(#121)으로 종료 시각이 밀렸을 때 종료 임박 알림을 다시 보낼지
