---
paths:
  - "domain/**/notification/**"
  - "api/**/notification/**"
---
알림(notification) 코드를 수정하기 전에 `docs/domains/notification.md`를 먼저 읽어라 — 유형별 카테고리·`target_id`·중복 정책 표, 문구를 저장하는 이유, `id` 정렬=시간 정렬 불변식(접기를 갱신이 아니라 재삽입으로 하는 근거), 30일 창, 적재 지점 배치 원칙이 거기 있다. 적재 지점을 새로 추가하거나 트랜잭션 경계를 바꾸면 [ADR 0018](../../docs/adr/0018-notification-center-append-and-read.md)을 함께 갱신하라.
