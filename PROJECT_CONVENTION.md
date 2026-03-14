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

## 2. 모듈 구조

```
ditto-server/
├── api/                    # 실행 가능한 Spring Boot 애플리케이션
├── common/                 # 비즈니스 무관 공통 유틸
├── domain/                 # 도메인 레이어 (엔티티, VO, 리포지토리)
├── infrastructure/         # 외부 인프라 (Redis, 외부 API 등)
└── buildSrc/               # Gradle convention plugins
```

### api
- Spring Boot 실행 모듈 (bootJar 생성 대상)
- `@Import`로 infrastructure 설정 클래스를 명시적으로 가져옴
  - 예: `@Import(RedisConfig::class)`
- Controller, 요청/응답 DTO 등 위치

### common
- **ditto 비즈니스 로직과 최대한 무관한 것들만** 위치
- 순수 Kotlin 모듈 (`kotlin-convention` 적용, Spring 의존성 없음)
- 포함 대상:
  - util 클래스
  - JSON converter / 직렬화 설정 (ObjectMapperFactory, DateTimeFormats)
  - exception 기본 구조 (WarnException, ErrorException, ErrorCode)
  - API 공통 응답 래퍼 (ApiResponse)
  - @Loggable 어노테이션

### domain
- 순수 Kotlin 모듈 (`kotlin-convention` 적용)
- 포함 대상:
  - Entity
  - VO (Value Object)
  - Repository (JPA, QueryDSL)

### infrastructure
- Spring 모듈 (`spring-convention` 적용, bootJar 비활성)
- 외부 인프라 연동 담당
- 모듈별 하위 패키지로 구분
  - 예: `redis/` 패키지 아래 `RedisConfig`, `application-redis.yml`
- 각 인프라별 설정 파일은 해당 모듈 resources에 `application-{name}.yml`로 관리

---

## 3. 모듈 의존성 흐름

```
api → common, domain, infrastructure
infrastructure → common, domain
domain → common
```

- `common`, `domain`은 Spring 의존성 없이 순수 Kotlin 유지
- `infrastructure`, `api`만 Spring convention 적용

---

## 4. 공통 기반 코드

### 예외 처리
- `WarnException` — 클라이언트 잘못 (400, 404 등), WARN 레벨 로깅, stacktrace 없음
- `ErrorException` — 서버 잘못 (500 등), ERROR 레벨 로깅, stacktrace 포함
- `ErrorCode` enum에 상태코드 + 코드 + 메시지 정의
- `GlobalExceptionHandler`가 자동 처리 (`@ResponseStatus(HttpStatus.OK)` — HTTP 상태는 항상 200, 에러 정보는 body로 전달)

### API 응답
- 모든 API는 `ApiResponse<T>`로 래핑
- **HTTP 상태는 항상 200**, 성공/실패는 `success` 필드로 구분
- 성공: `ApiResponse.ok(data)` → `{ "success": true, "data": {...} }`
- 에러: `ApiResponse.error(errorCode, message)` → `{ "success": false, "error": { "statusCode": 400, "code": "0001", "message": "..." } }`

```json
// 성공 응답
{ "success": true, "data": { "id": 1, "name": "홍길동" }, "error": null }

// 에러 응답
{ "success": false, "data": null, "error": { "statusCode": 400, "code": "0001", "message": "잘못된 요청값입니다." } }
```

### 직렬화
- `ObjectMapperFactory.create()`로 공통 ObjectMapper 생성
- `JacksonConfig`에서 Spring Bean으로 등록
- 날짜 포맷: `LocalDate` → `yyyy-MM-dd`, `LocalDateTime` → `yyyy-MM-dd HH:mm:ss`

### 보안
- Spring Security + API Key 방식
- `/api/**` 요청에만 `X-API-Key` 헤더 필수
- `/health`: 인증 없이 허용 (CloudFront 헬스체크)
- `/actuator/**`: 인증 없이 허용 (네트워크 레벨 보호)
- `/docs/**`, `/swagger-ui/**`: 인증 없이 허용 (API 문서)
- 그 외 경로: 전부 차단 (403)
- API Key는 환경변수 `API_KEY`로 주입 (로컬: `local-dev-key`, 프로덕션: Secrets Manager)

### 로깅
- `@Loggable`: 클래스/메서드에 붙이면 AOP 자동 로깅 (메서드명, 파라미터, 반환값, 실행시간, 예외)
- `RequestIdFilter`: 요청마다 MDC에 `requestId` 주입
- Logback 프로필별 분리:
  - `local`: 컬러 콘솔, requestId 포함, Hibernate SQL DEBUG
  - `prod`: JSON 구조화 로그 (CloudWatch 파싱 최적화)

### 프로필 (application.yml)
- `application.yml`: 공통 설정 (로컬 기본)
- `application-prod.yml`: 프로덕션 설정 (DB, 로깅, 보안 등)
- `application-test.yml`: 테스트 설정 (H2, 테스트용 API Key)
- ECS에서 `SPRING_PROFILES_ACTIVE=prod`로 활성화

---

## 5. 테스트 코드 컨벤션

### 테스트 프레임워크
- **Kotest** (통합 테스트): FreeSpec 스타일, 한글 테스트명 사용
- **JUnit5** (컨트롤러 문서화 테스트): `@DisplayName`에 한글, 메서드명은 영어
- MockK 사용 (모킹 필요 시)

### 테스트 네이밍 규칙

**Kotest (FreeSpec)** — 한글 문자열로 테스트명 작성:
```kotlin
"유저 생성" - {
    "정상적인 요청이면 유저가 생성된다" {
        // ...
    }
}
```

**JUnit5** — 메서드명은 영어, 한글 설명은 `@DisplayName`에 작성:
```kotlin
@Test
@DisplayName("정상적인 요청이면 유저가 생성된다")
fun createUser() {
    // ...
}
```

> **JUnit5 테스트에서 백틱 한글 메서드명을 사용하지 말 것.** 반드시 `@DisplayName`을 사용한다.

### 테스트 커버리지
- **Jacoco** 사용, `kotlin-convention`에서 자동 설정
- **최소 커버리지: 50%** — `check` 태스크에서 검증, 미달 시 빌드 실패
- CI에서 `./gradlew build check`로 커버리지 검증 포함
- 커버리지 제외 대상 (sonar-convention): `config/**`, `*Application*`, `*Config*`, `*Request*`, `*Response*`, `*Dto*`

### 통합 테스트 베이스 클래스: `IntegrationTest`
- 통합 테스트는 반드시 **`IntegrationTest`를 상속**해서 작성
- 위치: `api/src/test/kotlin/com/ditto/api/support/IntegrationTest.kt`
- 자동으로 처리하는 것들:
  - `@SpringBootTest` 적용
  - `SpringExtension` 등록 (Kotest + Spring 연동)
  - `DatabaseCleanExtension` 등록 (매 테스트 전 H2 전체 테이블 truncate)

> **`@SpringBootTest`를 직접 붙이거나 `FreeSpec`을 직접 상속하지 말 것.**

### 통합 테스트 작성법
```kotlin
class UserServiceTest(
    private val userService: UserService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {
    "유저 생성" - {
        "정상적인 요청이면 유저가 생성된다" {
            val result = userService.create(UserFixture.createRequest())
            result.name shouldBe "홍길동"
        }
    }
})
```

### 모킹 정책
- `mockk`는 사용하되, **`@MockBean` / `@SpyBean` 등 스프링 컨텍스트를 오염시키는 어노테이션은 사용하지 않음**

### test-fixtures
- `java-test-fixtures` 플러그인으로 엔티티 팩토리 분리
- 테스트 코드에서 매번 객체를 직접 생성하지 않고 fixture 팩토리를 통해 생성

### 통합 테스트 우선
- 최대한 `IntegrationTest` 기반 통합 테스트로 작성
- 순수 로직, util 등에만 단위 테스트 사용

---

## 6. API 문서화 (REST Docs + Swagger UI)

### 구조
- **restdocs-api-spec**을 사용하여 REST Docs 테스트 → OpenAPI 스펙 생성 → Swagger UI 렌더링
- 테스트가 통과해야만 API가 문서에 노출됨 (테스트 기반 문서 정확성 보장)
- Gradle convention: `restdocs-convention` (api 모듈에 적용)
- Swagger UI 접속 시 인증 불필요 (`/docs` 경로 공개)
- Swagger UI에서 API 테스트 시 **Authorize** 버튼으로 API Key 입력 필요

### 문서 생성 흐름
```
./gradlew test
  → REST Docs 테스트 실행 → 스니펫 생성
  → openapi3 태스크 자동 실행 (finalizedBy)
  → src/main/resources/static/docs/openapi.yaml 생성
  → 서버 실행 시 /docs 접속 → Swagger UI 렌더링
```

### 문서화 테스트 베이스 클래스: `RestDocsTest`
- 컨트롤러 문서화 테스트는 반드시 **`RestDocsTest`를 상속**해서 작성
- 위치: `api/src/test/kotlin/com/ditto/api/support/RestDocsTest.kt`
- 제공하는 것들:
  - `mockMvc`: `@AutoConfigureMockMvc` + `@AutoConfigureRestDocs` 자동 설정
  - `objectMapper`: `ObjectMapperFactory.create()`로 생성 (프로젝트 공통 직렬화 설정 사용)
  - `withApiKey()`: MockMvc 요청에 `X-API-Key` 헤더를 자동 추가하는 확장 함수
  - `@ActiveProfiles("test")`: 테스트 전용 설정 자동 적용
- JUnit5 기반 (REST Docs + MockMvc 호환을 위해 Kotest가 아닌 JUnit5 사용)

> **문서화 테스트와 통합 테스트는 베이스 클래스가 다름:**
> - 비즈니스 로직 통합 테스트 → `IntegrationTest` (Kotest FreeSpec)
> - 컨트롤러 문서화 테스트 → `RestDocsTest` (JUnit5)

### 문서화 테스트 작성법

모든 API 응답은 HTTP 200이며, `success` 필드로 성공/실패를 구분한다.

```kotlin
class SomeControllerTest : RestDocsTest() {

    @Test
    @DisplayName("어떤 API 설명")
    fun someApi() {
        val request = SomeRequest("value")

        mockMvc.perform(
            post("/api/some")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "some-api",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("카테고리")
                            .summary("API 요약")
                            .description("상세 설명")
                            .requestFields(
                                fieldWithPath("field").description("필드 설명"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("ID"),
                                fieldWithPath("data.name").description("이름"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build()
                    )
                )
            )
    }
}
```

### 필수 import
```kotlin
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
```

### 문서화 규칙
- 새 API 작성 시, 반드시 문서화 테스트도 함께 작성
- `tag`는 도메인 단위로 그룹핑 (예: `"User"`, `"Auth"`, `"System"`)
- `summary`는 한 줄로 API 역할 설명
- request/response 필드는 빠짐없이 문서화
- `/api/**` 경로 테스트 시 반드시 `.withApiKey()` 호출
- 접속 경로: `https://api.ditto.pics/docs`

---

## 7. Git 전략

### 브랜치
- **`main`이 근본 브랜치** (항상 배포 가능한 상태 유지)
- 이슈 먼저 생성 → `feature/<이슈번호>` 브랜치 → MR → 최소 1 Approve → 머지
- `main` 직접 push 금지

### 커밋 메시지
```
<type>. <설명>
```

| type       | 용도 | 예시 |
|------------|---|---|
| `feat`     | 새 기능 | `feat. 유저 회원가입 API` |
| `fix`      | 버그 수정 | `fix. 이메일 검증 오류 수정` |
| `docs`     | 문서 변경 | `docs. README 업데이트` |
| `refactor` | 리팩토링 | `refactor. UserService 책임 분리` |
| `test`     | 테스트 추가/수정 | `test. 유저 생성 통합테스트 추가` |
| `build`    | 빌드/설정 변경 | `build. Gradle 의존성 업데이트` |
| `style`    | 코드 포맷팅 | `style. 불필요한 import 제거` |

### MR(Merge Request) 규칙
- `main`으로의 직접 push 금지
- MR 생성 시 관련 이슈 번호 연결
- **최소 1명의 Approve** 필수
- CI(빌드 & 테스트 & 커버리지 50%) 통과 필수
- **머지 방식: Squash and merge** (피처 브랜치의 커밋들을 하나로 합쳐서 main에 머지)
- 스쿼시 머지 커밋 메시지는 커밋 메시지 컨벤션을 따름

---

## 8. 기타 규칙
- 정적분석: SonarCloud 연동 (`sonar-convention`)
- 인프라 설정 import: `api` 모듈에서 `@Import`로 명시적으로 가져오기
- profile별 설정 파일: `application.yml` (공통), `application-prod.yml` (프로덕션), `application-test.yml` (테스트)
