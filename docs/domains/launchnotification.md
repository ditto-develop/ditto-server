# launchnotification 도메인

앱 출시 알림 신청. 로그인하지 않은 웹 방문자가 "출시되면 알려 주세요"로 이메일을 남긴다. 지금은 안드로이드만.
메일 자동 발송은 없다 — 어드민이 CSV로 내려받아 직접 보낸다.

## 용어

- `LaunchNotificationRequest`(신청 1건 — 이메일·플랫폼·동의 버전·동의 시각·발송 시각), `LaunchPlatform`(플랫폼 enum, 값 추가 전용).
- API 표면: `POST /api/v1/launch-notification-requests`(요청 DTO는 엔티티와 이름이 겹치지 않게 `LaunchNotificationSignupRequest`).

## 불변식

- (이메일, 플랫폼)당 한 행 (`launch_notification_request_uk_1`). 이메일은 저장 전에 trim·소문자로 맞춘다.
- 재신청은 성공 응답 + 동의 시각·버전 갱신. 응답으로 등록 여부를 드러내지 않는다.
- `privacyConsent`가 true가 아니면 저장하지 않는다(0001). 동의 시각은 증빙이라 실제 시각(시각 오버라이드 미적용).
- 이메일은 앱 로그·메트릭에 남기지 않는다. 컨트롤러에 `@Loggable`을 붙이지 않고, DTO의 `email`은 `@Mask`.
- IP당 1분 5회(`TOO_MANY_REQUESTS` 0005). 배경: [ADR 0045](../adr/0045-launch-notification-public-endpoint.md).

## 상태 전이

```
(신청) notified_at = null ──어드민 "발송 완료"──> notified_at = 발송 시각 ──30일(RETENTION_DAYS)──> 파기(행 삭제)
```

- 발송 완료는 화면을 연 시점의 마지막 미발송 id(`upToId`) 이하만 바꾼다. 내보낸 뒤 들어온 신청이 "보냄"으로 묻히지 않게 내보내기도 같은 상한을 쓴다.
- 안내 전 신청은 파기하지 않는다. 파기는 `LaunchNotificationPurgeScheduler`(매일 04:20, 실제 시각).
- 삭제 요청은 개인정보 보호책임자 메일로 받아 수동으로 지운다(자동화 범위 밖).

## 어드민

- `/admin/launch-notifications` — 건수만 보여 준다(이메일은 화면에 없음). CSV 내려받기·발송 완료.
- CSV는 `email,requested_at`. `=`·`+`·`-`·`@`로 시작하는 주소는 앞에 `'`를 붙인다(엑셀 수식 실행 방지).
- 내려받기·발송 완료는 어드민 표시명과 건수만 로그로 남긴다.

## 메트릭

`ditto_launch_notification_requests_total{platform, result=created|refreshed|rate_limited}` — 신청 추이와 남용 감시.

## 핵심 파일

- 엔티티·리포지토리: `domain/src/main/kotlin/com/ditto/domain/launchnotification/`, 스키마 `domain/db/V20261019115506_출시 알림 신청 테이블 추가.sql`
- API: `api/src/main/kotlin/com/ditto/api/launchnotification/`, 리미터 `api/.../support/ratelimit/`
- 어드민: `api/src/main/kotlin/com/ditto/api/admin/launchnotification/`, `templates/launch-notification/summary.html`
