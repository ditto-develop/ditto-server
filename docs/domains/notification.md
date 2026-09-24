# notification (알림)

알림 센터(피그마 `7.2 알림 센터`). 사건이 생길 때 **행을 적재**하고, 화면이 최신순 커서 페이징으로 읽고 읽음 표시한다.

인앱 기록과 함께 **푸시(FCM)도 이 도메인이 보낸다** — 적재 입구(`NotificationAppender`)에서
행이 생긴 알림만 `PushNotifier`가 내보내므로, 중복 정책이 푸시에도 그대로 적용된다.

## 용어

- `Notification` — 알림 한 행. **수신자 1명당 1행**이다(같은 사건이라도 받는 사람마다 문구가 다르고 읽음도 따로다).
- `NotificationType` — 알림 유형. 카테고리·`target_id`의 대상·중복 정책을 이 enum이 정한다.
- `NotificationCategory` — 화면 필터 칩(`MATCHING`/`CHAT`/`SYSTEM`). **컬럼이 아니라 유형에서 파생**된다. "전체" 칩은 값이 아니라 필터 없음이다.
- `DuplicatePolicy` — 같은 대상에 다시 발생했을 때의 처리(`ALLOW`/`ONCE_PER_TARGET`/`COLLAPSE_UNREAD`).
- `NotificationAppender` — 알림을 남기는 유일한 입구. 실패를 삼킨다.
- `NotificationWriter` — 실제 저장. `REQUIRES_NEW`로 자기 트랜잭션에서 커밋한다.
- `NotificationMessages` — 문구 한곳 모음(정본은 기획의 "알림 문구" 표).
- `MemberDevice` — 푸시 주소록 한 줄. 앱이 FCM 에서 받은 디바이스 토큰의 소유 회원. 회원 1명이 여러 행(폰·태블릿).
- `PushNotifier` — 적재된 알림 한 행을 푸시로 변환·발송. 토글 게이트·deepLink·뱃지가 여기 있다.
- `PushSender` — FCM 어댑터(infrastructure). 비동기 발송, 무효 토큰(`UNREGISTERED`)을 콜백으로 돌려준다.
- `SystemNotice` — 어드민이 보낸 시스템 공지 한 건의 이력(제목·본문·발송자·대상 수·수신 수). 수신자별 행은 `Notification`이다.

## 유형 표 (FE 계약)

| 유형 | 카테고리 | `target_id` | 중복 정책 | 적재 지점 |
|---|---|---|---|---|
| `QUIZ_OPENED` | MATCHING | `quiz_set.id`(이번 주 대표 셋) | 대상당 1회 | `WeeklyNotificationScheduler`(월 00:00, 프로퍼티) → `QuizNotifier` — 활성 회원 전원 |
| `QUIZ_CLOSING_SOON` | MATCHING | `quiz_set.id`(이번 주 대표 셋) | 대상당 1회 | `WeeklyNotificationScheduler`(수 18:00, 프로퍼티) → `QuizNotifier` — 어느 셋도 끝내지 않은 활성 회원 |
| `MATCH_RESULT` | MATCHING | `quiz_set.id` | 대상당 1회 | `MatchingScheduler` → `MatchResultNotifier` |
| `NO_MATCH` | MATCHING | `quiz_set.id` | 대상당 1회 | `MatchingScheduler` → `MatchResultNotifier` (매칭 풀에 들었지만 후보 0명) |
| `GROUP_FORMED` | MATCHING | `chat_room.id`(그룹) | 대상당 1회 | `GroupMatchService.joinGroupMatch` |
| `REMATCH_REQUESTED` | MATCHING | `rematch.id` | 대상당 1회 | `MemberReviewController.submitAnswer` → `RematchNotifier` — 먼저 "원한다"를 낸 사람의 상대 |
| `REMATCH_REJECTED` | MATCHING | `rematch.id` | 대상당 1회 | 같은 지점 — `CANCELLED(NOT_MUTUAL)` 시 원했던 쪽 |
| `REMATCH_MATCHED` | MATCHING | `chat_room.id`(재매칭) | 대상당 1회 | `RematchChatRoomOpener.reserve` |
| `MATCH_REQUESTED` | MATCHING | `personal_match.id` | 대상당 1회 | `PersonalMatchController.requestMatch` → `PersonalMatchNotifier` |
| `MATCH_ACCEPTED` | MATCHING | `personal_match.id` | 대상당 1회 | `PersonalMatchController.acceptMatch` → `PersonalMatchNotifier` |
| `MATCH_REJECTED` | MATCHING | `personal_match.id` | 대상당 1회 | `PersonalMatchController.rejectMatch` → `PersonalMatchNotifier` |
| `REVIEW_REQUEST` | MATCHING | `chat_room.id`(끝난 방) | 대상당 1회 | `ChatRoomLifecycleScheduler`·`ChatController.end` → `ReviewRequestNotifier` |
| `REVIEW_REMINDER` | MATCHING | `chat_room.id`(끝난 방) | 대상당 1회 | `WeeklyNotificationScheduler`(월 09:00, 프로퍼티) → `ReviewReminderNotifier` — 최근 7일 안에 열린 활성 회원의 미완료 평가 |
| `CHAT_ROOM_OPENED` | CHAT | `chat_room.id`(열린 방) | 대상당 1회 | `ChatRoomLifecycleScheduler` → `ChatRoomOpenedNotifier` |
| `CHAT_MESSAGE` | CHAT | `chat_room.id` | 안읽은 것 접기 | `ChatStompController` → `ChatMessageNotifier` |
| `CHAT_NO_MESSAGE` | CHAT | `chat_room.id` | 대상당 1회 | `ChatRoomLifecycleScheduler` → `ChatNoMessageNotifier`(개방 12시간 후, 프로퍼티) |
| `CHAT_ENDING_SOON` | CHAT | `chat_room.id` | 대상당 1회 | `ChatRoomLifecycleScheduler` → `ChatEndingSoonNotifier` |
| `VOTE_CREATED` | CHAT | `chat_room.id` | 제한 없음* | `ChatVoteController.createVote` → `ChatVoteNotifier` |
| `VOTE_CLOSED` | CHAT | `chat_room.id` | 제한 없음* | `ChatVoteController.close` → `ChatVoteNotifier` |
| `SYSTEM_NOTICE` | SYSTEM | `system_notice.id` | 제한 없음 | `AdminNoticeController` → `SystemNoticeFacade` → `SystemNoticeNotifier` — 활성 회원 전원 |

`QUIZ_OPENED`·`QUIZ_CLOSING_SOON`·`MATCH_RESULT`·`NO_MATCH`의 대상이 퀴즈셋인 것은 화면 이동용이 아니라 **"주마다 한 번"의 판정 기준**이다. `QUIZ_OPENED`·`QUIZ_CLOSING_SOON`은 한 주에 1:1·그룹 셋이 나란히 열려도 알림은 하나라 **문항이 있는 셋 중** id 가 가장 작은 셋을 대표로 삼는다(문항 수 문구도 그 셋 기준). 문항 있는 셋이 없으면 보내지 않는다. 오픈은 활성 회원 전원, 마감 임박은 그중 어느 셋도 `COMPLETED`하지 않은 회원에게 간다(하나라도 끝냈으면 참여자다 — 문구가 하나라 회원당 한 번). 회원+유형만으로 막으면 평생 한 번만 알린다. `MATCH_REQUESTED`·`MATCH_REJECTED`의 대상이 매칭 건인 것도 같은 이유다 — 한 주에 여러 명에게 신청하고 여러 명에게서 받을 수 있어 회원+유형으로 막으면 첫 건만 알린다.

**노매칭 알림의 수신자는 매칭 풀에 든 회원이다**(`MatchmakingService.matchingPoolMemberIds` — 퀴즈 완료자에서 배치의 제외 정책에 걸린 사람을 뺀 집합). 참여하지 않은 사람에게 "답이 닿지 않았다"는 성립하지 않고, 정지·성사로 풀에서 빠진 사람에게는 틀린 안내다.

**신청 알림은 수신자에게만, 수락·거절 알림은 신청자에게만 간다.** 행위를 한 본인은 자기가 누른 것이라 알릴 것이 없다. 이동 경로는 둘 다 전용 화면이 없어 `MATCH_RESULT`와 같은 `/matching/`이다. 그룹 초대 거절은 알리지 않는다(`docs/domains/match.md` — 거절당한 그룹의 다른 구성원에게는 알리지 않는다); 1:1만 알린다.

\* `VOTE_CREATED`·`VOTE_CLOSED`가 중복을 유형으로 막지 않는 이유: 실제 발행이 생성(방당 열린 투표 1개)·close 의 멱등(실제로 닫은 요청만)으로 이미 한 번이고, 같은 방의 다음 투표 시작·마감은 정당한 새 알림이다. 시작 문구의 "일요일 자정까지"는 방 종료 시각이다 — 투표에는 마감 시각이 없다(`vote.md`). 방 종료 동반 마감(`ROOM_ENDED`)은 알리지 않는다 — 방이 끝났다는 사실은 평가 요청 알림이 이미 말한다.

## 불변식

- **문구는 발송 시점에 확정해 저장한다.** 조회 때 다시 렌더하지 않는다 — 곧 붙을 푸시와 센터가 같은 문구여야 하고, 센터는 "그때 무엇을 알렸는가"의 기록이라 닉네임이 바뀐 뒤 다시 렌더하면 사실이 달라진다. 채팅 SYSTEM 메시지와 **반대 방향**의 선택이다([ADR 0018](../adr/0018-notification-center-append-and-read.md)).
- **`id` 정렬 = 시간 정렬.** 커서 페이징이 `id` 하나만 쓰므로, 알림을 접을 때 기존 행을 갱신하지 않고 **지우고 다시 삽입**한다. 이 불변식을 깨면 커서가 두 키를 다뤄야 한다.
- **안읽음은 `read_at == null` 이다.** 채팅처럼 읽음 커서 하나로 접지 않는다 — 화면이 개별 읽음을 요구한다.
- **보관·조회 창은 30일이다**(`Notification.RETENTION_DAYS`). 조회가 그 밖을 자르고, 같은 기준으로 purge 배치가 지운다. 미읽음 수(배지)도 같은 창을 써야 한다 — 창이 어긋나면 배지가 0이 되지 않는다.
- **설정의 알림 토글은 이 목록을 막지 않는다.** 토글(`member_notification_setting`)은 푸시 수신 동의이고 센터는 인앱 기록이다. 채팅 알림을 끈 사람도 센터에서는 새 메시지를 본다.
- **적재는 비즈니스 트랜잭션을 되돌리지 않는다.** `REQUIRES_NEW` + 실패 흡수. 반대급부로 롤백된 작업의 알림이 드물게 남을 수 있고, 그건 감수한다.
- **알림 행이 곧 처리 완료 표시다.** 사용자 삭제가 행을 남기는 이유가 이것이다. "대상당 1회" 유형은 존재 검사로 막으므로, 스케줄러가 같은 방·같은 퀴즈셋을 매 주기 다시 집어와도 알림은 하나다. 별도 플래그나 outbox 가 없다(`RematchChatRoomOpener`·`EndedChatReviewOpener`와 같은 수렴 루프).
- **재매칭 방 종료에는 평가 요청을 알리지 않는다.** 재매칭 채팅은 평가를 열지 않기 때문이다(#132). `ReviewRequestNotifier`가 `REMATCH`를 걸러낸다.
- **탈퇴 완전 삭제는 알림도 지운다.** 본문에 닉네임·메시지 미리보기(개인정보)가 들어 있다.
- **사용자 삭제는 행을 남긴다(`deleted_at`).** 중복 검사가 행의 존재를 보므로 지워버리면 수렴 루프를 도는 스케줄러가 같은 알림과 푸시를 다시 내보낸다. 지운 알림은 목록·미읽음 수·전체 읽음에서 빠지고, 30일 뒤 purge 가 다른 행과 함께 지운다. 사용자에게는 되돌릴 수 없다.
- **중복 검사는 보관 기간까지만 유효하다.** 행이 purge 되면 존재 검사가 다시 통과하므로, 수렴 루프의 스캔 범위는 30일보다 짧아야 한다. 지금은 모두 그렇다 — `CHAT_ENDING_SOON`은 종료 6시간 창, `CHAT_NO_MESSAGE`는 개방 12시간 뒤부터 6시간 창(둘 다 대화 메시지가 생기거나 이미 알린 방은 조회 쿼리가 **방 단위로** 뺀다 — 매분 도는 조회라 멤버별 존재 검사를 반복하지 않게. 그래서 한 명이라도 적재됐으면 나머지의 적재 실패는 재시도되지 않는다), `GROUP_NOT_FORMED`는 최근 2주 주차, `REVIEW_REMINDER`는 최근 7일 안에 열린 평가만 본다(평가에는 마감이 없어 창이 없으면 오래된 미완료가 매주 딸려 나온다), `MATCH_RESULT`·`NO_MATCH`는 매칭 배치가 마감 2주 안의 셋만 집는다(`MatchingBatchFacade.RETRY_WINDOW_DAYS` — 후보 0건 셋은 영원히 후보가 없어 하한이 없으면 매주 다시 잡힌다), 나머지는 이번 주기 처리분만 받는다.
- **한 토큰 = 한 회원.** `member_device.token` 단독 유일 제약이 강제한다. 토큰은 기기의 것이라 로그아웃해도
  그대로이므로, 공용 기기에서 다른 회원이 로그인하면 행 추가가 아니라 소유자 갱신이다 — 갱신하지 않으면
  이전 회원의 알림이 남의 폰에 뜬다. 등록은 멱등이고(앱이 실행·토큰 갱신 때마다 재호출),
  응답의 `registered`는 "이번 호출로 이 회원 소유가 됐는지"다(멱등 재호출이면 `false`, 실패 아님).
  토큰은 불투명 문자열이다 — 형식을 해석·검증하지 않는다.

## 푸시 발송

적재와 같은 입구에서 나간다: `NotificationAppender` → 행 생성 시 `PushNotifier.push` → `PushSender`(FCM).
발송은 비동기(fire-and-forget)라 적재·비즈니스 흐름을 기다리게 하지 않고, 준비 실패도 삼킨다.

- **토글 게이트** — `MemberNotificationSetting.allowsPush`: MATCHING→`matching`, CHAT→`chat`.
  SYSTEM 은 막지 않는다(설정 화면의 세 토글 어디에도 속하지 않고, `marketing`은 마케팅 수신 동의라 공지와
  다른 개념 — 어드민 공지가 생길 때 재검토). 행이 없는 회원은 기본값으로 판단한다.
- **payload** — `notification`(title·body는 저장 문구 그대로) + `data`(전부 문자열: `notificationId`·`type`·`deepLink`).
- **deepLink** — FE 라우트 경로, **끝 슬래시 필수**(`trailingSlash: true`). 채팅 계열은 방 종류로 갈린다
  (GROUP→`/chat/group/{id}/`, PERSONAL·REMATCH→`/chat/one-on-one/{id}/` — FE 방 목록과 같은 이분법).
  `MATCH_RESULT`→`/matching/`, `QUIZ_OPENED`·`QUIZ_CLOSING_SOON`→`/quiz/current/`, `REVIEW_REQUEST`·`REVIEW_REMINDER`→방 경로+`rate/`, `REMATCH_REQUESTED`·`REMATCH_REJECTED`→쌍이 나온 그룹 방 경로+`rate/`(의사를 제출하는 화면), `SYSTEM_NOTICE`→없음(탭하면 앱만 열림 — `target_id`는 추적용).
  방이 지워졌으면 deepLink 없이 보낸다.
- **뱃지** — 미읽음 수 API 와 같은 기준(`Notification.retentionFrom()` — 30일 창·실제 시각)이라
  인앱 벨 배지와 앱 아이콘 뱃지가 같은 수다.
- **ttl** — 시효가 있는 알림만 짧게 준다(`CHAT_MESSAGE` 1시간, `CHAT_ENDING_SOON`·`QUIZ_CLOSING_SOON` 6시간 — 종료·마감 6시간 전
  알림이라 지나면 무의미, `CHAT_ROOM_OPENED`·`CHAT_NO_MESSAGE`·`QUIZ_OPENED` 3일 — 방이 열려 있는 72시간·퀴즈 응답 기간 월~수). 나머지는 FCM 기본(4주). 꺼져 있던 기기에 지난 채팅 알림이 몰리는 것을 막는다.
- **죽은 토큰 정리** — 발송 결과의 `UNREGISTERED` 토큰을 `PushDeadDeviceCleaner`가 지운다(FCM 콜백
  스레드라 자기 트랜잭션). 방치하면 실패율이 쌓여 FCM 이 발송량을 제한한다.

## 적재 지점을 어디에 두는가

원칙은 **커밋된 뒤에, 사건을 아는 곳에서**다.

- 스케줄러가 부르는 경로(`MATCH_RESULT`·`NO_MATCH`·`CHAT_ROOM_OPENED`·`REVIEW_REQUEST`·`CHAT_ENDING_SOON`·`CHAT_NO_MESSAGE`)는 전이가 커밋된 뒤에 부르므로 롤백된 작업의 알림이 남지 않는다.
- 시각이 트리거인 경로(`QUIZ_OPENED`·`QUIZ_CLOSING_SOON`·`REVIEW_REMINDER` — `WeeklyNotificationScheduler`)는 사건이 일어나는 코드 지점이 없다. `MatchingScheduler`처럼 실제 시각의 cron 으로 돌고, 그 시각에 활성 셋이 없으면(어드민이 늦게 만들면) 그 주 알림은 없다. 수렴 루프가 아니다.
- 요청 경로(`MATCH_REQUESTED`·`MATCH_ACCEPTED`·`MATCH_REJECTED`·`VOTE_CREATED`·`VOTE_CLOSED`·`SYSTEM_NOTICE`·`REMATCH_REQUESTED`·`REMATCH_REJECTED`)는 **컨트롤러(또는 트랜잭션 없는 facade)가 서비스 커밋 뒤에** 부른다. 서비스의 `@Transactional` 안에 두면 `REQUIRES_NEW` 적재가 먼저 커밋돼 롤백된 요청의 알림이 나가고, 커넥션을 잡은 채 푸시 준비 조회를 한다. 컨트롤러가 흐름을 알게 되는 대가는 감수한다 — 진입점이 늘면 `facade` 계층으로 모은다(아래 TODO).
- 트랜잭션 안에서 부르는 경로(`GROUP_FORMED`)는 그 사실을 아는 곳이 거기뿐이라 남겨 뒀다. 롤백 시 알림만 남을 수 있다는 것을 알고 택했다.
- 실시간 경로(`CHAT_MESSAGE`)는 **브로드캐스트 뒤에** 둔다 — 전달이 적재를 기다리지 않아야 한다.

## 시스템 공지

어드민이 `/admin/notices`에서 제목·본문을 넣고 보내면 활성 회원 전원에게 `SYSTEM_NOTICE`가 적재·푸시된다.

- **이력은 `system_notice`에 따로 남긴다.** `notification` 행은 수신자별 기록이라 "언제 누가 무엇을 몇 명에게"를 볼 수 없고 30일 뒤 지워진다. 이력은 제목·본문·발송자(ID·이름·이메일 스냅샷)·대상 수·수신 수를 담고 상태 컬럼은 없다.
- **발송은 어드민 요청 안에서 동기로 끝난다.** 다른 전원 알림(`QUIZ_OPENED`)과 같다. `SystemNoticeFacade`가 **대상 조회 → 이력(대상 수) 커밋 → 적재 → 수신 수 기록** 순으로 트랜잭션 없이 조율한다. 조회를 먼저 하는 이유는 조회가 실패하면 이력 없이 오류로 끝나야 하기 때문이다 — 사람이 결과를 보는 경로라 스케줄러처럼 삼키지 않는다.
- **`recipient_count`가 NULL 이면 발송 중이다.** 발송이 길어져 화면이 먼저 돌아와도(앞단 타임아웃) "0명"이 아니라 "발송 중 · 대상 N명"으로 보여야 어드민이 다시 보내지 않는다 — `SYSTEM_NOTICE`는 중복을 막지 않아 두 번 보내면 전원이 두 번 받는다. 발송 도중 서버가 멈추면 NULL 로 남는다. 상태 컬럼(FAILED)이 없으니 그건 어드민이 판단한다(화면 안내). 회원 수가 커져 발송이 앞단 타임아웃을 넘기게 되면 상태 컬럼과 비동기 작업을 `QUIZ_OPENED`와 함께 다시 본다.
- 회원 한 명의 적재 실패는 `NotificationAppender`가 삼키고 로그로 남으며, 수신 수는 실제 적재된 수다. 자동 재시도는 없다 — 어드민이 다시 보낸다.
- **문구 길이 제한은 알림과 같다**(`Notification.TITLE_MAX_LENGTH`·`BODY_MAX_LENGTH`). 검증·정규화는 `SystemNotice.create`가 한다 — 앞뒤 공백을 지우고 줄바꿈을 LF 로 맞춘 뒤 센다(브라우저 폼은 textarea 줄바꿈을 CRLF 로 보내 화면 `maxlength`를 통과한 500자가 서버에서 길어진다). 검증에 걸리면 입력값을 폼에 다시 채워 준다.
- **푸시 토글에 걸리지 않는다.** 운영 공지(업데이트·점검)를 전제로 한다. 이벤트 안내처럼 마케팅 성격의 공지를 같은 경로로 보내면 `marketing` 수신 거부를 무시하게 되므로, 그 전에 공지 종류를 나눠 토글을 태울지 정한다(TODO).

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/notifications` | 최신순 커서 페이징. `category`·`cursor`·`size`(기본 20, 최대 100) |
| GET | `/api/v1/notifications/unread-count` | 홈 헤더 벨 배지용 |
| PUT | `/api/v1/notifications/{id}/read` | 개별 읽음(멱등). 남의 알림은 404 |
| PUT | `/api/v1/notifications/read-all` | 전체 읽음. `readCount` 반환 |
| DELETE | `/api/v1/notifications/{id}` | 개별 삭제. 남의 알림·이미 지운 알림은 404. `deletedCount`는 항상 1(전체 삭제와 형식을 맞춘 값) |
| DELETE | `/api/v1/notifications` | 전체 삭제. `category`를 주면 그 칩만, 보관 창 안만. `deletedCount` 반환 |
| POST | `/api/v1/notifications/devices` | 푸시 디바이스 토큰 등록(멱등·소유권 이전). 앱 전용 |
| DELETE | `/api/v1/notifications/devices/{token}` | 토큰 해제(멱등). 남의 토큰은 404. 로그아웃·탈퇴 직전에 앱이 호출 |

응답 필드와 예시는 `/docs`(REST Docs → Swagger UI). FE 연동 가이드는 레포 위키의 "알림 센터 API 연동 가이드".

## 시각

**실제 시각으로 동작한다** — 어드민 시각 오버라이드(`ServerTimeProvider`)를 쓰지 않는다. `created_at`을 JPA Auditing 이 실제 시각으로 채우므로, 조회 창을 가짜 시각으로 계산하면 오버라이드가 미래일 때 방금 온 알림이 창 밖으로 밀려 사라진다.

## TODO (미확정)

- 탈퇴 완전 삭제 시 `member_device` 정리 — FE 가 탈퇴 전 해제를 부르지만 서버측 보강 필요 (#154)
- 실시간 배지 — 현재는 폴링/재조회. STOMP 개인 큐 여부 미정
- 시스템 공지의 운영/마케팅 구분과 `marketing` 토글 연동 — 이벤트 안내를 같은 경로로 보내기 전에
- 시스템 공지 대상 조건(전원 외) 발송 — 필요해지면
- 채팅 연장(#121)으로 종료 시각이 밀렸을 때 종료 임박 알림을 다시 보낼지
- 요청 경로의 알림 호출(신청·수락·거절·투표 시작·마감·채팅 종료)을 컨트롤러에서 `facade`로 모으기 — 진입점이 늘면 누락 위험
- 평가 리마인드는 마감 개념 없이 독촉 한 번이다(#211 결정). 기획이 평가 기한을 정하면 제출 거부 여부와 함께 다시 본다
- `CHAT_ROOM_OPENED`는 복구 경로가 없다(개방 커밋 직후 실패·재시작이면 알림 없음). 필요해지면 `CHAT_ENDING_SOON`처럼 최근 개방 방을 다시 집어오는 수렴 루프로
