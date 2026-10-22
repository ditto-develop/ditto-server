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

## JS 테스트

- `api`의 `check`는 `jsTest`도 돌린다. `api/src/test/js/**/*.test.js`를 Node 내장 러너(`node --test`)로 실행하므로 **빌드 환경에 Node가 있어야 한다**. CI는 `setup-node`로 설치한다.
- 대상은 어드민 화면 JS 중 DOM을 건드리지 않는 계산 파일이다(예: `static/admin/js/stats-calc.js`). 브라우저에서는 일반 `<script>`로, 테스트에서는 `require`로 읽히게 파일 끝에서 `module.exports`를 내보낸다. npm 패키지는 쓰지 않는다.
- Sonar 는 `src/main/kotlin`만 보므로 JS 커버리지는 게이트에 들어가지 않는다.
