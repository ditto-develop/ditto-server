---
paths:
  - "api/**/admin/stats/**"
  - "api/**/templates/stats/**"
  - "api/**/static/admin/js/stats*.js"
---
운영 지표(`/admin/stats`) 코드를 수정하기 전에 `docs/admin/stats.md`를 먼저 읽어라 — 지표마다 세는 기준(관리자 제외·탈퇴 포함 여부·만 나이)과 대조 SQL이 거기 있다. 서버는 집계 결과만 넘기고 계산은 `stats-calc.js`가 한다([ADR 0046](../../docs/adr/0046-admin-stats-render-in-browser.md)). 지표 기준을 바꾸면 그 문서를 함께 고쳐라.
