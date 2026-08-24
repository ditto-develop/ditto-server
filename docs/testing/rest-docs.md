# 테스트 — API 문서화 (REST Docs + Swagger UI)

> 컨트롤러 문서화 테스트를 작성/수정하기 전에 아래 **필수 규칙**을 먼저 적용하라. 그 아래는 흐름·템플릿이다.

## ⚠️ 필수 규칙 (먼저 적용)

- 컨트롤러 문서화 테스트는 `RestDocsTest`를 상속한다(JUnit5). 비즈니스 통합 테스트(`IntegrationTest`, Kotest)와 베이스가 다르다.
- **새 API는 문서화 테스트를 반드시 함께 작성**한다(테스트 통과해야 문서에 노출됨).
- `/api/**` 요청엔 반드시 `.withApiKey()`를 호출한다.
- request/response 필드는 **빠짐없이** 문서화한다.
- **nullable 필드도 샘플 응답에는 non-null 값이 한 번은 나와야 한다.** 샘플이 전부 null이면 타입을 추론하지 못해
  그 필드가 openapi.yaml 스키마에서 통째로 빠진다(`fieldWithPath(...).optional()`을 붙여도 마찬가지).
  목록이면 값이 있는 항목을 하나 섞고, 서버가 항상 null을 주는 필드(미지원 필드 등)만
  `fieldWithPath(...).type(JsonFieldType.X)`로 타입을 명시한다. 배경: [#140](https://github.com/ditto-develop/ditto-server/issues/140).
- `tag`는 도메인 단위로 그룹핑(예: `"User"`, `"Auth"`, `"System"`), `summary`는 한 줄.

---
아래는 **흐름·템플릿**이다.

## 생성 흐름

```
./gradlew test
  → REST Docs 테스트 → 스니펫 생성
  → openapi3(finalizedBy) → src/main/resources/static/docs/openapi.yaml
  → 서버 /docs 접속 → Swagger UI
```

`restdocs-api-spec`으로 테스트 통과 시에만 문서화(테스트 기반 정확성). convention: `restdocs-convention`(api). 접속: `https://api.ditto.pics/docs` (Swagger UI는 공개, 테스트 시 Authorize로 API Key 입력).

## `RestDocsTest`가 제공하는 것

- `mockMvc`(`@AutoConfigureMockMvc`+`@AutoConfigureRestDocs`), `objectMapper`(`ObjectMapperFactory.create()`), `withApiKey()`(요청에 `X-API-Key` 추가), `@ActiveProfiles("test")`.
- 위치: `api/src/test/kotlin/com/ditto/api/support/RestDocsTest.kt`. JUnit5 기반.

## 작성 예

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
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build()
                    )
                )
            )
    }
}
```

필수 import:
```kotlin
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
```
