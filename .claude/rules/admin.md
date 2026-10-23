---
paths:
  - "api/**/admin/**"
---
어드민 코드를 수정하기 전에 `docs/domains/auth.md`의 어드민 인가([ADR 0006](../../docs/adr/0006-admin-authz-filter-path-check.md))를 확인하라. 새 어드민 JSON 엔드포인트는 `/api/v1/admin` prefix 아래 두면 `JwtAuthenticationFilter`가 자동 403 보호한다(서버 렌더 UI는 `/admin/**` 별도 세션 체인).

어드민 기능은 프론트(화면 JS·템플릿)에서 표현할 수 있는 건 웬만하면 프론트에서 처리하고 백엔드 구조는 단순하게 둔다. 어드민은 프로덕션 기능이 아니라서, 그 때문에 도메인·서비스·DTO 에 복잡성을 더하지 않는다.
- 서버는 조회·집계 결과를 그대로 넘기고 비율·라벨·분류·합산은 JS 가 한다. 순수 계산은 DOM 없는 파일로 떼어 `api/src/test/js`에서 `node --test`로 검증한다([ADR 0046](../../docs/adr/0046-admin-stats-render-in-browser.md)이 운영 지표에 적용한 예).
- 쿼리는 리포지토리 하나·결과 DTO 하나·group by 키만 쓰고, 서비스는 결과를 담기만 한다. 값 하나짜리 래퍼 DTO 는 만들지 않는다.
- 어드민 전용 개념은 domain 엔티티에 두지 않는다. SQL 조건으로 들어가야 하는 변환만 서버에 둔다.
- 기존 코드에 선례가 없는 쿼리·구조 패턴은 쓰기 전에 사용자 동의를 받는다.
