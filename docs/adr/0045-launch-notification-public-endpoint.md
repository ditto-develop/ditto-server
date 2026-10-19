# ADR 0045 — 출시 알림 신청은 API Key 전용 체인에 두고, IP당 한도를 인메모리로 건다

- 상태: Accepted (2026-10-19)
- 근거: code-evidence (#324)

## Context

웹(ditto.pics)을 브라우저로 열면 앱 다운로드 안내만 보이게 바뀌고, 아직 출시 전인 안드로이드는 출시되면 알려 줄 방문자의 이메일을 받는다. 신청자는 로그인하지 않았다.

- `/api/**`는 X-API-Key가 필요하지만 그 키는 웹 번들에 들어 있어 사실상 공개다. 키만으로는 남용을 못 막는다.
- 서버에는 Redis도 레이트 리미터 라이브러리도 없다. ECS 서비스는 태스크 1개다(채팅 SimpleBroker도 같은 전제, [ADR 0009](0009-websocket-stomp-auth.md)).
- 서버는 ALB 뒤에 있고 `server.forward-headers-strategy`를 켜지 않았다. `remoteAddr`는 ALB 주소다.
- "이미 신청됨" 응답은 그 이메일이 등록돼 있다는 사실을 드러낸다.

## Decision

- 경로 하나(`POST /api/v1/launch-notification-requests`)를 API Key 전용 체인(@Order(4))에 추가한다. 리뷰 로그인·네이티브 로그인과 같은 "로그인 전, 앱/웹이 직접 부르는 호출"이다.
- IP당 1분 5회 고정 윈도우를 인메모리(`FixedWindowRateLimiter`)로 건다. 넘기면 `TOO_MANY_REQUESTS`(0005). 키가 IP라 무한히 늘 수 있어, 1만 개를 넘으면 만료 윈도우를 지운다.
- 클라이언트 IP는 `X-Forwarded-For`의 **맨 끝** 값이다(`ClientIp`). ALB가 자기가 본 주소를 끝에 붙이고, 앞쪽은 클라이언트가 마음대로 넣을 수 있다. 전역 `forward-headers-strategy`는 켜지 않는다 — 다른 경로의 리다이렉트·URL 생성이 바뀔 수 있어 범위를 이 기능으로 한정했다.
- 같은 (이메일, 플랫폼) 재신청은 성공으로 응답하고 동의 시각·버전만 갱신한다. 동시 삽입이 유일 제약에 걸리면 새 트랜잭션에서 갱신으로 다시 돈다.

## Consequences

- IP를 바꿔 가며 보내는 공격은 막지 못한다. 그 경우 쌓이는 건 이메일 행뿐이고 메일을 자동으로 보내지 않으므로(범위 밖), 피해는 저장량과 내보내기 목록 오염이다.
- 레플리카를 늘리면 IP당 한도가 레플리카 수만큼 늘어난다. 그때는 공유 저장소(Redis 등) 기반으로 바꾼다.
- ALB를 거치지 않고 서버에 닿는 경로가 생기면 `X-Forwarded-For`를 위조해 한도를 피할 수 있다. 지금은 ALB만 경유한다고 본다(추정).
- 재배포하면 윈도우가 초기화된다. 1분 한도라 영향이 작다.

## Links

- 도메인 규칙: [launchnotification](../domains/launchnotification.md)
- 인증 체인: [auth](../domains/auth.md), [ADR 0002](0002-security-filter-chain-deny-all-default.md)
