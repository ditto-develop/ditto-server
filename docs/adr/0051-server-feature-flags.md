# ADR 0051 — 서버 기능 플래그는 DB 한 행 + 인스턴스별 1분 캐시로 두고 어드민 화면에서 바꾼다

- 상태: Accepted (2026-11-04)
- 근거: discussion (IMP-1 배포 순서 — #365)

## Context

IMP-1 은 기존 메시지 목록·실시간 토픽에 새 메시지 종류(`QUIZ_HINT`·`QUIZ_CARD`)와 보낸 사람이 없는 메시지(`senderId: null`)를 섞는다. 지금 FE 는 모르는 종류를 일반 말풍선으로 그려(JSON 이 그대로 보인다) `senderId`로 연속 메시지를 묶는다. 그래서 BE 를 먼저 배포하면 FE 가 따라오기 전까지 화면이 깨진다.

API 를 v1/v2 로 나누는 방법은 맞지 않았다. 실시간 메시지는 같은 방 사람들이 한 STOMP 토픽을 함께 구독해 버전별로 가를 수 없고, 앱은 웹뷰로 FE 를 불러와 FE 배포 뒤 옛 클라이언트가 오래 남지 않는다.

기존 스위치는 환경변수(`ADMIN_QA_TOOLS_ENABLED`, [ADR 0039](0039-admin-qa-tools-env-switch.md))뿐이다. 바꾸려면 태스크 정의를 고쳐 배포해야 한다.

## Decision

- 플래그는 Kotlin enum(`FeatureFlagKey`)으로 정의하고, 상태는 `feature_flag` 테이블에 플래그당 한 행으로 둔다. **행이 없으면 꺼짐**이다 — 새 플래그를 추가해도 마이그레이션 없이 꺼진 채 시작한다.
- 읽기(`FeatureFlags`)는 인스턴스마다 **1분 TTL** 로 전체 행을 한 덩어리 캐시한다. 메시지 전송·매분 스케줄러가 매번 DB 를 읽지 않게 하려는 것이다. 바꾼 인스턴스는 커밋 뒤 캐시를 버려 곧바로 반영하고, 다른 인스턴스는 최대 1분 늦다.
- 어드민 화면 `/admin/feature-flags`(서버 렌더, 세션 체인)에서 켜고 끈다. QA 도구 스위치와 무관하게 열린다 — 실서비스 기능을 여닫는 화면이다. 바꾼 어드민 회원 ID 를 남긴다.
- 행은 **마지막으로 켠 시각**(`enabled_at`)을 갖는다. 이미 켜진 플래그를 다시 켜도 유지하고, 끄면 지운다. 기능을 켜기 전부터 진행 중이던 대상에 소급 적용하지 않을 때 쓴다(첫 대화 힌트는 켠 뒤 열린 방에만 남긴다, [ADR 0052](0052-pair-room-answer-compare.md)). 켠 시각은 서버 시각(`ServerTimeProvider`)으로 남겨, 비교 대상인 방 개방 시각과 같은 시계를 본다.
- 첫 플래그 `QUIZ_CHAT` = 첫 대화 힌트 생성 + 질문 카드 전송. 꺼져 있으면 힌트를 만들지 않고 카드 전송을 `FEATURE_DISABLED`(0006)로 거부하며, 답변 비교 응답의 `canSendCard`도 false 다. 기록·답변 비교·분포 API 는 새 엔드포인트라 플래그 밖이다.

## Consequences

- 얻음: BE 를 언제 배포해도 기존 화면이 그대로다. 배포 순서는 BE(꺼짐) → FE → 어드민에서 켜기이고, 켜고 끄는 데 배포가 필요 없다.
- 비용: 테이블 1개와 수동 SQL 1개. 서버가 여러 대면 최대 1분 동안 서버마다 값이 다를 수 있다 — 한 사람이 보낸 카드가 어떤 서버에서는 거부되는 1분이 생긴다. 지금은 1대라 바꾼 서버가 곧 전체다.
- 테스트는 같은 스프링 컨텍스트를 공유하므로, 플래그를 쓰는 테스트는 앞뒤로 캐시를 비운다(`FeatureFlags.evict`).
- 플래그 이름이 DB 키다. enum 이름을 바꾸면 그 플래그는 꺼진다.

## Links

- 이슈 #365. 핵심 파일: `domain/.../system/entity/FeatureFlag.kt`·`FeatureFlagKey.kt`, `api/.../system/featureflag/`, `api/.../admin/featureflag/AdminFeatureFlagController.kt`, 스키마 `domain/db/V20261104220607_기능 플래그 테이블 추가.sql`.
- 관련 ADR: [0039](0039-admin-qa-tools-env-switch.md)(환경변수 스위치).
