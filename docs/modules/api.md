# api 모듈

실행 가능한 Spring Boot 애플리케이션. `bootJar` 생성 대상이며 `spring-convention`(+ `restdocs-convention`) 적용.

## 담는 것

- 컨트롤러, 요청/응답 DTO
- `config/` — `SecurityConfig`, `ApiKeyAuthFilter`, `ApiKeyProperties`, `GlobalExceptionHandler`, `JacksonConfig`, `LoggingAspect`, `OpenApiConfig`, `RequestIdFilter`
- 인프라 설정은 `@Import`로 명시적으로 가져온다 (예: `@Import(RedisConfig::class)`)

## 컨트롤러 규칙

- 요청 경로는 **각 핸들러 메서드의 매핑 애너테이션에 전체 경로로 명시**한다. 클래스 레벨 `@RequestMapping`으로 prefix를 묶지 않는다 — 한 곳만 보고 전체 URL을 알 수 있어 `grep`·추적이 쉽다.
  - `@GetMapping("/api/v1/quiz-sets/current-week")` (O) / 클래스 `@RequestMapping` 후 메서드 상대경로 (X)
- 폼 백킹 빈(서버 렌더링)·요청 바인딩 객체의 주생성자는 **public**으로 둔다 (스프링이 바인딩 시 인스턴스화).
- `LocalDateTime` 파라미터는 `ISO_LOCAL_DATE_TIME`(`yyyy-MM-ddTHH:mm`)을 스프링 기본 변환으로 바인딩한다 — 별도 `@DateTimeFormat` 불필요.

## 응답 형태 (상세)

래핑 규칙(ApiResponse·HTTP 200·success)은 AGENTS.md 참조. 구체적 형태:
- 성공: `ApiResponse.ok(data)` → `{ "success": true, "data": {...}, "error": null }`
- 실패: `ApiResponse.error(errorCode, message)` → `{ "success": false, "data": null, "error": { "statusCode": 400, "code": "0001", "message": "..." } }`
- `ApiResponse`·예외 기반구조 정의는 `common` 모듈(`docs/modules/common.md`).

## 보안 (상세)

경로별 인증 규칙은 AGENTS.md 참조. API Key는 환경변수 `API_KEY`로 주입(로컬 기본값 `local-dev-key`, 프로덕션은 Secrets Manager).

## 문서화

- 새 API는 문서화 테스트(REST Docs)를 동반한다. 작성법은 `docs/testing/rest-docs.md`.
