plugins {
    id("spring-convention")
    id("com.epages.restdocs-api-spec")
}

dependencies {
    testImplementation(Dependencies.SPRING_RESTDOCS_MOCKMVC)
    testImplementation(Dependencies.RESTDOCS_API_SPEC_MOCKMVC)
}

openapi3 {
    setServer("https://api.ditto.pics")
    title = "Ditto API"
    description = """
        Ditto 서비스 API 문서입니다.

        ## 인증
        모든 API 요청에는 `X-API-Key` 헤더가 필요합니다.
        API Key는 백엔드 담당자에게 발급받으세요.

        ```
        X-API-Key: <발급받은 API Key>
        ```

        Swagger UI에서 테스트하려면 우측 상단의 **Authorize** 버튼을 클릭하고 API Key를 입력하세요.
    """.trimIndent()
    version = "v1"
    format = "yaml"
    outputDirectory = "src/main/resources/static/docs"
    outputFileNamePrefix = "openapi"
}

val addSecurityScheme by tasks.registering {
    doLast {
        val outputFile = file("src/main/resources/static/docs/openapi.yaml")
        if (outputFile.exists()) {
            var content = outputFile.readText()

            content = content.replace(
                "components:\n  schemas:",
                """
                components:
                  securitySchemes:
                    ApiKey:
                      type: apiKey
                      in: header
                      name: X-API-Key
                      description: API 인증 키. 백엔드 담당자에게 발급받으세요.
                  schemas:
                """.trimIndent()
            )

            content += "\nsecurity:\n- ApiKey: []\n"

            outputFile.writeText(content)
        }
    }
}

afterEvaluate {
    tasks.named("openapi3") { finalizedBy(addSecurityScheme) }
    tasks.named("test") { finalizedBy("openapi3") }
}
