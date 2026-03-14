# Ditto Server - Project Convention

> 이 문서는 ditto-server 프로젝트의 기술 스택, 모듈 구조, 코딩 컨벤션을 정의합니다.
> **새로운 Claude 세션을 열 때 반드시 이 파일을 먼저 읽고 시작해주세요.**

---

## 1. 기본 프로젝트 세팅

| 항목 | 버전 |
|---|---|
| Java | 21 (LTS) |
| Kotlin | 1.9.25 |
| Spring Boot | 3.5.11 |
| Gradle | 8.14 |

- 빌드 시스템: Gradle Kotlin DSL, **멀티모듈**
- `buildSrc` 기반 convention plugin 패턴 사용
  - `kotlin-convention` : 순수 Kotlin 모듈용 (Jacoco 커버리지 포함)
  - `spring-convention` : Spring Boot 모듈용 (`kotlin-convention` 상속)
  - `restdocs-convention` : REST Docs + OpenAPI 문서화 (`spring-convention` 상속)
  - `sonar-convention` : SonarCloud 정적분석
  - `DependencyVersions.kt` : 의존성 버전 중앙 관리

---

## 2. 모듈 구조 / 의존성 흐름

```
api → common, domain, infrastructure
infrastructure → common, domain
domain → common
```

- `common`, `domain`은 Spring 의존성 없이 순수 Kotlin 유지
- `infrastructure`, `api`만 Spring convention 적용

---

## 3. 공통 기반 코드

### API 응답
- **HTTP 상태는 항상 200**, 성공/실패는 `success` 필드로 구분
- 성공: `{ "success": true, "data": {...} }`
- 에러: `{ "success": false, "error": { "statusCode": 400, "code": "0001", "message": "..." } }`

### 보안
- `/api/**` 요청에만 `X-API-Key` 헤더 필수
- `/health`, `/actuator/**`, `/docs/**`: 인증 없이 허용
- 그 외 경로: 차단 (403)

---

## 4. 테스트 코드 컨벤션

### 테스트 네이밍
- **Kotest**: 한글 문자열 테스트명
- **JUnit5**: 메서드명 영어, `@DisplayName`에 한글

> JUnit5에서 백틱 한글 메서드명 사용 금지.

### 커버리지: 최소 50% (Jacoco)

### 베이스 클래스
- 통합 테스트 → `IntegrationTest` (Kotest FreeSpec)
- 컨트롤러 문서화 → `RestDocsTest` (JUnit5, MockMvc)

---

## 5. API 문서화 (REST Docs + Swagger UI)

- 테스트 통과 → 스니펫 → openapi.yaml → Swagger UI
- `/docs` 접속 시 인증 불필요, API 테스트 시 Authorize 필요
- `/api/**` 테스트 시 `.withApiKey()` 필수

---

## 6. Git 전략

- `main` 직접 push 금지
- 이슈 → `feature/<이슈번호>` → MR → 1 Approve → Squash merge
- 커밋: `<type>. <설명>` (feat, fix, docs, refactor, test, build, style)
- CI: 빌드 + 테스트 + 커버리지 50% 통과 필수
