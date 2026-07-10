---
paths:
  - "api/**/admin/**"
---
어드민 코드를 수정하기 전에 `docs/domains/auth.md`의 어드민 인가([ADR 0006](../../docs/adr/0006-admin-authz-filter-path-check.md))를 확인하라. 새 어드민 JSON 엔드포인트는 `/api/v1/admin` prefix 아래 두면 `JwtAuthenticationFilter`가 자동 403 보호한다(서버 렌더 UI는 `/admin/**` 별도 세션 체인).
