# ADR (Architecture Decision Records)

되돌리기 어렵거나 대안이 있었던 결정을, 나중에 "왜 이렇게 했지?"를 답할 수 있게 한 건 = 한 파일로 남긴 기록.

## 목록

- [0001 — AI 에이전트 문서 계층 도입](0001-agent-doc-hierarchy.md)
- [0002 — 다중 SecurityFilterChain로 인증 계층 구성 (미매칭 경로는 deny-all)](0002-security-filter-chain-deny-all-default.md)
- [0003 — JWT subject를 내부 memberId로 고정 (소셜 식별자 결합 제거)](0003-jwt-subject-member-id.md)
- [0004 — OAuth 콜백을 302 FE 리다이렉트로, refreshToken은 HttpOnly 쿠키로](0004-oauth-callback-redirect-and-cookie.md)
- [0005 — 카카오 동의항목을 콜백 쿼리가 아닌 GET /api/v1/users/me로 전달](0005-kakao-consent-via-me-api.md)
- [0006 — 어드민 인가를 @PreAuthorize가 아닌 JwtAuthenticationFilter 경로 검사로](0006-admin-authz-filter-path-check.md)
- [0007 — 1:1 매칭을 순수 컴포넌트 파이프라인으로 분해 (대칭 하드필터 + 동점 무작위)](0007-matching-pure-pipeline.md)
- [0008 — 매칭 엔티티 유니크 모델링: 1:1 페어 정규화 + 그룹 참여/거절 테이블 분리](0008-matching-entity-uniqueness-modeling.md)
- [0009 — WebSocket(STOMP) 인증: 핸드셰이크 개방 + 프레임 레벨 인증·구독 인가](0009-websocket-stomp-auth.md)
- [0010 — 주간 식별자 SSOT를 weekStartedOn(그 주 월요일 날짜)으로 정규화](0010-week-identifier-week-started-on.md)
- [0011 — 평가 데이터 모델: 진행 단위/응답 분리와 상태 표현 최소화](0011-review-progress-and-answer-split.md)
- [0014 — ECS 태스크 메모리 예산 재배분: Metaspace 128m→256m, 힙 비율 45%→35%](0014-ecs-metaspace-heap-rebalance.md)
- [0015 — chat_room의 원본 식별자는 (source_type, source_id)로 접두어를 맞춘다](0015-chat-room-source-type-naming.md)

## 언제 ADR을 쓰는가

- 되돌리기 어려운 결정(스키마·인증 구조·외부 연동처럼 나중에 바꾸기 비싼 것).
- 대안이 있었고 그중 하나를 골랐을 때 — 고른 이유와 버린 이유가 나중에 궁금해질 것.
- 코드만 봐서는 "왜?"가 안 보이는 결정(비결정적 무작위, 경로 prefix 인가, 사후 기록한 굳은 결정 등).

반대로 코드·이름으로 자명하거나 쉽게 되돌릴 수 있는 선택은 ADR로 남기지 않는다.

## 쓰는 법

1. [`0000-template.md`](0000-template.md)를 복제한다.
2. 다음 빈 번호(현재 최댓값 + 1)로 `NNNN-영문-슬러그.md` 파일명을 짓는다.
3. 상태를 `Proposed`/`Accepted`로 두고 ## Context / ## Decision / ## Consequences / ## Links를 채운다.
4. 폐기되면 해당 ADR을 지우지 말고 상태를 `Superseded(→ NNNN)`로 바꿔 이력을 남긴다.
5. 이 목록에 한 줄 링크를 추가한다.
