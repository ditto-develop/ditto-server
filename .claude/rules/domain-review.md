---
paths:
  - "domain/**/review/**"
  - "api/**/review/**"
---
평가(review) 코드를 수정하기 전에 `docs/domains/review.md`를 먼저 읽어라 — 불변식(종료 이벤트 멱등·자기 자신 제외·제출은 최종·별점 1~5·코멘트 50자)과 단독 `Review` 명명 금지 규칙이 거기 있다. 상태를 추가하려거든 `answered_at`·`invalidated_at`처럼 이미 그 사실을 말하는 값이 있는지 먼저 확인하라.
