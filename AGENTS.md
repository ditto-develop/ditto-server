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
  - `openapi.yaml`은 이 명령이 만드는 산출물이라 git이 추적하지 않는다. 새로 클론했다면 한 번 돌려야 `/docs` 스웨거가 뜬다.
- 정적분석 업로드: `./gradlew sonarqube`
- 로컬 실행: `./gradlew :api:bootRun`
- 끝내기 전 **가장 좁은 관련 테스트부터** 실행한다.

## Modules (코드를 어디에 둘지 — 위치 안내용)

새 코드를 어느 모듈에 둘지 정하는 용도다. **이 절은 규칙이 아니다** — 각 모듈의 작성 규칙은 `docs/modules/`에 있고,
해당 모듈 파일을 열면 `.claude/rules/module-*.md`가 자동으로 그 문서를 가리킨다.

- `api` — 실행 모듈(bootJar). 컨트롤러, 요청/응답 DTO, config.
- `common` — 비즈니스 무관 공통(예외·ApiResponse·직렬화·`@Loggable`). 순수 Kotlin.
- `domain` — 엔티티·VO·리포지토리(JPA·QueryDSL). 순수 Kotlin.
- `infrastructure` — 외부 인프라 연동(Redis 등). Spring 모듈.

의존 방향: `api → common·domain·infrastructure`, `infrastructure → common·domain`, `domain → common`.
`common`·`domain`은 **Spring 의존성 금지**(순수 Kotlin 유지).

## Core conventions (전역 불변식)

- 모든 API 응답은 `ApiResponse<T>`로 래핑한다. **HTTP 상태는 항상 200**, 성공/실패는 `success` 필드로 구분.
- 예외: `WarnException`=클라이언트 잘못(4xx, WARN), `ErrorException`=서버 잘못(5xx, ERROR), 코드/메시지는 `ErrorCode` enum.
- 생성자 주입 선호(필드 주입 금지). Kotlin nullability를 의도적으로 쓰고 Java `Optional`을 남발하지 않는다.
- 버전 숫자를 문서에 적지 말 것 — Gradle 파일이 SSOT.

## Testing

- 머지 게이트(둘 다 통과해야 함): Jacoco 모듈별 50% + SonarCloud New Code 80%.
- 테스트 작성/수정 시 `docs/testing/`의 **필수 규칙을 먼저 적용**한다(베이스 클래스·모킹·네이밍·문서화).

## Security

- `/api/**`는 `X-API-Key` 헤더 필수. `/health`·`/actuator/**`·`/docs/**`·`/swagger-ui/**`는 공개, 그 외 403.
- `/api/v1/admin/**`는 어드민 미인가 시 403(`JwtAuthenticationFilter` 경로 검사). 배경: `docs/adr/0006-admin-authz-filter-path-check.md`.

## Git

- `main` 직접 push 금지. 이슈 먼저 → `feature/<이슈번호>` 브랜치 → MR(1 Approve + CI 통과) → **Squash and merge**.
- 커밋 메시지: `<type>. <설명>` (type: feat/fix/docs/refactor/test/build/style).

## Safety

- 시크릿·토큰·프로덕션 데이터·로컬 자격증명을 커밋하지 않는다.
- 생성된 파일은 명시적 요청 없이 수정하지 않는다.
- DB 스키마 변경은 마이그레이션(`domain/db`, `create_sql.sh`)과 관련 테스트를 동반한다.
- 공개 API 동작이 바뀌면 문서화 테스트(REST Docs)를 갱신한다.

## 문서 갱신 (작업하며 함께)

이 문서 체계는 기초다 — 코드가 자라면 문서도 함께 갱신한다.

- 설계 방향을 새로 정하거나 바꾸면 → `docs/adr/`에 ADR 추가(기존 형식: Context/Decision/Consequences/Links, 번호 증가).
- 도메인 불변식·상태전이를 확정하면 → 떠나기 전에 `docs/domains/<도메인>.md`의 TODO를 그 자리에서 채운다.
- 새 도메인을 추가하면 → `docs/domains/<d>.md` + `.claude/rules/domain-<d>.md` 쌍을 만든다.
- 새 모듈/엔드포인트 유형이 생기면 → 해당 `docs/`와 가리키는 `.claude/rules/` stub을 추가한다.
- 리뷰에서 같은 지적이 3회 반복되면 → rule 또는 검사기(lint/CI)로 승격한다.
