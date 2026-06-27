# ADR 0004 — OAuth 콜백을 302 FE 리다이렉트로, refreshToken은 HttpOnly 쿠키로

- 상태: Accepted
- 근거: commit-message (5a86457·91c8147 본문) + code KDoc

> 사후 기록 — 커밋·코드에서 재구성(2026-06-27).

## Context

카카오 OAuth 콜백은 카카오가 브라우저를 BE 호스트로 직접 리다이렉트시킨다. 콜백이 JSON으로 토큰을 반환하면 브라우저가 BE 호스트의 JSON 응답에 갇혀 FE SPA로 돌아갈 수 없다. 또한 refreshToken을 URL/쿼리 파라미터로 전달하면 XSS(JS 탈취)·액세스 로그·브라우저 히스토리에 노출된다.

## Decision

콜백은 FE 콜백 페이지로 302 리다이렉트한다(`ditto.front.oauth-callback-url`, `OAuthService.getAuthCallbackUrl`로 URL 생성). accessToken·signupRequired는 쿼리 파라미터로, refreshToken은 HttpOnly·Secure·SameSite 쿠키로 전달한다(`RefreshTokenCookieFactory`로 응집, `CookieProperties`로 환경별 secure/sameSite/path 제어). refresh API도 쿠키 입출력으로 하고 CORS `allowCredentials=true`로 전환한다. PENDING(신규) 회원은 토큰 없이 같은 path로 보내 FE가 path+토큰 유무로 신규/기존을 구분한다.

## Consequences

- 얻음: 브라우저 흐름이 FE로 정상 복귀, refreshToken이 JS·로그·히스토리에 노출되지 않음.
- 비용: 쿠키 환경별 설정(secure/sameSite, `COOKIE_SECURE` env)과 CORS `allowCredentials=true` 운영 부담, FE가 쿼리 파라미터/쿠키 두 경로로 토큰을 받아야 함.
- 이 쿠키 보안 기조는 이후 카카오 동의항목 전달 방식 결정([ADR 0005](0005-kakao-consent-via-me-api.md))의 근거로 인용됨.

## Links

- commits: 5a86457(302 리다이렉트 전환), 91c8147(refreshToken HttpOnly 쿠키 전환)
- `api/.../config/auth/RefreshTokenCookieFactory.kt`(KDoc 'XSS 토큰 탈취 차단'), `CookieProperties.kt`, `FrontProperties.kt`, `auth/controller/OAuthController.kt`(302)
