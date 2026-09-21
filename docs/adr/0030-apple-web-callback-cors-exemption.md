# ADR 0030 — 애플 도메인을 CORS 허용 origin에 넣는다

- 상태: Accepted (2026-09-21)
- 근거: 프로덕션 재현 + spring-web 6.2.16 소스 확인

## Context

[ADR 0023](0023-apple-web-login-form-post-callback.md)으로 `POST /api/v1/users/social-login/APPLE/callback`을 열었는데, 실제 웹 로그인이 화면에 `Invalid CORS request`만 남기고 끝났다.

애플 웹 로그인은 `response_mode=form_post`라 콜백이 **애플 도메인에서 우리 서버로 오는 크로스사이트 POST**다. 브라우저가 `Origin: https://appleid.apple.com`을 붙이는데, 허용 목록이 `ditto.pics`·`www.ditto.pics`뿐이라 거절됐다.

`DefaultCorsProcessor.handleInternal`은 preflight 여부와 무관하게 Origin을 먼저 대조하고, 어긋나면 `rejectRequest`로 403과 그 본문을 내보낸다. 컨트롤러는 실행되지 않아 애플리케이션 로그에도 남지 않는다.

프로덕션 재현:

```
POST .../social-login/APPLE/callback + Origin: https://appleid.apple.com  → 403 Invalid CORS request
같은 요청에서 Origin 헤더만 제거                                          → 200
POST /admin/oauth/apple/callback    + Origin: https://appleid.apple.com   → 302
```

어드민 콜백이 멀쩡한 건 `/admin/**`이 CORS 설정에 등록돼 있지 않아서다.

## Decision

허용 origin 목록에 `https://appleid.apple.com`을 더한다. 로컬·테스트는 `application.yml`, 프로덕션은 `.aws/task-definition.json`의 `CORS_ALLOWED_ORIGINS`다.

### 왜 이 경로만 CORS 검사에서 빼지 않았나

콜백 경로만 `CorsConfiguration`을 따로 등록해 Origin을 안 가리는 방법도 있다(`UrlBasedCorsConfigurationSource`는 등록 순서대로 **처음 매칭된 설정 하나만** 쓴다). 실제로 그렇게 만들어 봤지만 되돌렸다 — 목록 한 줄과 비교해 실질 차이가 작고 코드만 늘었다.

특히 **와일드카드로 여는 건 안전하지 않다.** 이 검사가 막고 있던 건 브라우저 밖 공격자가 아니라 **피해자 브라우저를 부리는 공격자**다.

- 공격자가 자기 애플 계정으로 로그인해 유효한 `id_token`을 얻는다.
- 그 값을 박은 자동 제출 폼을 남의 사이트에 올린다.
- 피해자가 그 페이지를 열면 브라우저가 우리 콜백으로 POST하고, 피해자는 **공격자 계정으로 로그인된다**(로그인 CSRF). 이후 올리는 사진·채팅이 공격자 계정에 쌓인다.

이때 요청의 `Origin`은 공격자가 바꿀 수 없다. 목록 방식은 이 요청을 계속 막는다. 같은 이유로 `"null"`도 넣지 않는다 — sandbox iframe으로 얼마든지 만들어낼 수 있는 값이다.

### 남는 것

- 애플 도메인이 `/api/**` 전체에 `allowCredentials=true`로 열린다. 악용하려면 애플 도메인에 악성 JS가 있어야 하고, 우리 API는 `Authorization` 헤더를 요구하며 refreshToken 쿠키는 `SameSite=Lax`라 크로스사이트 XHR에 실리지 않는다.
- 애플이 `Origin`을 다른 값(예: `null`)으로 보내면 다시 403이다. 증상이 이 이슈와 같아 바로 드러난다.

## Consequences

- 얻음: 웹 애플 로그인이 동작한다. 코드 변경이 없다.
- 비용: 프로덕션 값이 `.aws/task-definition.json`에만 있어 **코드만 봐서는 이 의존을 알 수 없다.** `SecurityConfigTest`에 회귀 테스트 둘을 둬서(애플 origin POST 통과 / 허용 안 된 origin 차단) 로컬·테스트 목록에서 빠지는 것만큼은 CI가 잡는다. 프로덕션 환경변수는 잡지 못하므로 `application.yml`에 배경 주석을 남겼다.
- **남은 공백 1 — 로그인 CSRF**: `state` 파라미터를 쓰지 않아 위 공격이 성립한다. 카카오 GET 콜백은 Origin 헤더가 아예 없어 CORS 검사도 안 걸리므로 이미 열려 있다. 두 제공자를 함께 고쳐야 하고 ADR 0023이 남긴 `SameSite=None` 문제와 얽혀 별도 이슈([#209](https://github.com/ditto-develop/ditto-server/issues/209))로 분리한다.
- **남은 공백 2 — refreshToken 쿠키**: `SameSite=Lax`(`application.yml`)라 크로스사이트 POST 응답에서 브라우저가 버릴 수 있다. 그러면 403은 풀려도 refreshToken만 안 남는다. 미확인이며, 배포 후 실제 브라우저로 확인한다.

## Links

- 이슈: [#208](https://github.com/ditto-develop/ditto-server/issues/208)
- 설정: `api/src/main/resources/application.yml`(로컬·테스트), `.aws/task-definition.json`(`CORS_ALLOWED_ORIGINS`)
- 관련: [ADR 0023](0023-apple-web-login-form-post-callback.md), [ADR 0028](0028-admin-apple-login-csrf-exemption.md)
