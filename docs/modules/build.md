# 빌드 / buildSrc

Gradle Kotlin DSL 멀티모듈. `buildSrc`의 convention plugin으로 빌드 설정을 중앙화한다.

## 의존성 버전

- 버전 숫자의 SSOT는 `buildSrc/src/main/kotlin/DependencyVersions.kt`. 문서·모듈 빌드 어디에도 숫자를 복붙하지 않는다.
- 새 프로덕션 의존성 추가 전, 왜 필요한지 설명하고 Kotlin/Spring plugin 호환을 확인한다.

## Convention plugin

- `kotlin-convention` — 순수 Kotlin 모듈용. Jacoco 커버리지 포함. (common·domain)
- `spring-convention` — Spring Boot 모듈용, `kotlin-convention` 상속. (infrastructure·api)
- `restdocs-convention` — REST Docs + OpenAPI, `spring-convention` 상속. (api)
- `sonar-convention` — SonarCloud 정적분석. (별도)

## 빌드 산출물

- `bootJar` 대상은 `api`만. `infrastructure`는 bootJar 비활성(라이브러리 jar).
