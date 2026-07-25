# ADR 0009 — WebSocket(STOMP) 인증: 핸드셰이크 개방 + 프레임 레벨 인증·구독 인가

- 상태: Accepted
- 근거: 실시간 채팅(#104) 도입

## Context

채팅 실시간 전송을 STOMP over WebSocket 으로 제공한다. 기존 인증은 `/api/**` 에 `ApiKeyAuthFilter`+`JwtAuthenticationFilter`(HTTP 헤더 `X-API-Key`/`Bearer`)로 걸려 있고, 미매칭 경로는 deny-all([ADR 0002](0002-security-filter-chain-deny-all-default.md))이다. 두 가지 제약이 있다:

- 브라우저 WebSocket API 는 핸드셰이크 요청에 커스텀 헤더(`X-API-Key`/`Authorization`)를 실을 수 없어, HTTP 필터로 핸드셰이크를 인증할 수 없다.
- Spring 내장 SimpleBroker 는 기본적으로 "누가 무엇을 구독하는지"를 인가하지 않는다. 인증된 회원이 남의 방 토픽(`/sub/chat/rooms/{id}`)을 구독해 메시지를 도청할 수 있다.

## Decision

- `/ws/**` 핸드셰이크 전용 `SecurityFilterChain`(`@Order(6)`, permitAll, STATELESS)을 추가한다. deny-all 체인은 `@Order(7)`로 밀어 마지막을 유지한다(ADR 0002 불변식 유지).
- 인증·인가는 STOMP 프레임 레벨에서 `StompAuthChannelInterceptor`(clientInboundChannel)가 수행한다.
  - **CONNECT**: STOMP 네이티브 헤더 `X-API-Key` + `Authorization: Bearer <JWT>` 검증(`/api/**` 와 동일 포스처). 성공 시 `MemberPrincipal` 을 세션 user 로 세팅, 실패 시 연결 거부.
  - **SUBSCRIBE**: destination 이 `/sub/chat/rooms/{id}` 형식이어야 하고, 그 방의 멤버(`chat_room_member`)여야 한다. 아니면 거부(도청 차단).
  - **SEND**: 방 멤버십은 `ChatService` 가 검증(REST 조회와 동일 규칙).
- 회원 존재·PENDING 게이트는 CONNECT 에서 검사하지 않는다. 서명된 JWT 는 발급 시점에 회원이 존재했음을 의미하고, 실제 접근은 방 멤버십으로 게이트되므로(PENDING 회원은 방이 없음) 충분하다.
- subject=memberId([ADR 0003](0003-jwt-subject-member-id.md)) 재사용.

## Consequences

- 얻음: 브라우저 호환 인증(핸드셰이크 헤더 제약 우회), 구독 인가로 메시지 기밀성 확보, 기존 토큰·키 소스 재사용.
- 비용: 인증 로직이 HTTP 필터와 STOMP 인터셉터 두 곳으로 나뉜다(같은 토큰·키를 두 경로가 검증) — 향후 공통화 여지.
- 체인 추가로 `@Order` 재정렬(deny-all 6→7). 새 공개 경로처럼 매처·deny-all 상호작용 검토 필요(ADR 0002 비용 항목).
- 스케일: 인메모리 SimpleBroker 는 레플리카를 넘지 못한다. 2번째 레플리카가 필요하면 `enableStompBrokerRelay`(RabbitMQ 등)로 교체한다(구독 인가 인터셉터는 유지).

## Links

- 이슈 #104. `api/.../chat/websocket/`(`WebSocketConfig`, `StompAuthChannelInterceptor`, `ChatStompController`), `config/SecurityConfig.kt`(`@Order(6)` ws 체인).
- 관련 ADR: [0002](0002-security-filter-chain-deny-all-default.md)(다중 체인 + deny-all), [0003](0003-jwt-subject-member-id.md)(subject=memberId).
