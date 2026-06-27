# AGENTS.md

ditto-server 에이전트용 공통 규칙(도구 중립 SSOT). 항상 읽히는 문서이므로 얇게 유지한다.
세부 규칙은 `.claude/rules/`(경로별)·`docs/`(온디맨드)에 두고, 여기서는 위치만 가리킨다.

## Project

Kotlin / Spring Boot 백엔드. Gradle Kotlin DSL **멀티모듈**, `buildSrc` convention plugin 패턴.

## Source of truth (복붙하지 말고 여기서 읽을 것)

- 의존성·버전: `buildSrc/src/main/kotlin/DependencyVersions.kt`, `*/build.gradle.kts`
- 런타임 설정: `*/src/main/resources/application*.yml`
- DB 마이그레이션: `domain/db/V*.sql` (반드시 `domain/db/create_sql.sh`로 생성)
- 모듈 상세: `docs/modules/`
- 테스트 가이드: `docs/testing/`
- 도메인 지식(용어·불변식·상태전이): `docs/domains/`
- 아키텍처 결정 배경: `docs/adr/`

## Commands

Gradle wrapper를 사용한다.

- 빌드 + 전체 검증(Jacoco 포함): `./gradlew build check`
- 테스트(REST Docs → OpenAPI 생성): `./gradlew test`
- 정적분석 업로드: `./gradlew sonarqube`
- 로컬 실행: `./gradlew :api:bootRun`
- 끝내기 전 **가장 좁은 관련 테스트부터** 실행한다.

## Modules (코드를 어디에 둘지 — 위치 안내용)

새 코드를 어느 모듈에 둘지 정하는 용도다. **이 절은 규칙이 아니다** — 각 모듈의 작성 규칙은
해당 모듈 파일을 열면 자동 로드되는 `.claude/rules/module-*.md`(핵심 규칙)와 `docs/modules/`(상세)를 따른다.

- `api` — 실행 모듈(bootJar). 컨트롤러, 요청/응답 DTO, config.
- `common` — 비즈니스 무관 공통(예외·ApiResponse·직렬화·`@Loggable`). 순수 Kotlin.
- `domain` — 엔티티·VO·리포지토리(JPA·QueryDSL). 순수 Kotlin.
- `infrastructure` — 외부 인프라 연동(Redis 등). Spring 모듈.

의존 방향: `api → common·domain·infrastructure`, `infrastructure → common·domain`, `domain → common`.
`common`·`domain`은 **Spring 의존성 금지**(순수 Kotlin 유지).

## Core conventions (전역 불변식)

- 모든 API 응답은 `ApiResponse<T>`로 래핑한다. **HTTP 상태는 항상 200**, 성공/실패는 `success` 필드로 구분.
- 예외: `WarnException`=클라이언트 잘못(4xx, WARN), `ErrorException`=서버 잘못(5xx, ERROR), 코드/메시지는 `ErrorCode` enum.
- 컨트롤러 핸들러는 매핑에 **전체 경로를 명시**한다(클래스 레벨 `@RequestMapping` prefix 금지).
- 생성자 주입 선호(필드 주입 금지). Kotlin nullability를 의도적으로 쓰고 Java `Optional`을 남발하지 않는다.
- 버전 숫자를 문서에 적지 말 것 — Gradle 파일이 SSOT.

## Testing

- 통합 테스트 우선: 단위(순수 로직)는 작게, 비즈니스 동작은 `IntegrationTest` 상속 통합 테스트로. 상세는 `docs/testing/`.
- 머지 게이트(둘 다 통과해야 함): Jacoco 모듈별 50% + SonarCloud New Code 80%.
- `@MockBean`/`@SpyBean` 금지(MockK 사용). 테스트 통과를 위해 단언을 약화시키지 않는다.

## Security

- `/api/**`는 `X-API-Key` 헤더 필수. `/health`·`/actuator/**`·`/docs/**`·`/swagger-ui/**`는 공개, 그 외 403.

## Git

- `main` 직접 push 금지. 이슈 먼저 → `feature/<이슈번호>` 브랜치 → MR(1 Approve + CI 통과) → **Squash and merge**.
- 커밋 메시지: `<type>. <설명>` (type: feat/fix/docs/refactor/test/build/style).

## Safety

- 시크릿·토큰·프로덕션 데이터·로컬 자격증명을 커밋하지 않는다.
- 생성된 파일은 명시적 요청 없이 수정하지 않는다.
- DB 스키마 변경은 마이그레이션(`domain/db`, `create_sql.sh`)과 관련 테스트를 동반한다.
- 공개 API 동작이 바뀌면 문서화 테스트(REST Docs)를 갱신한다.
