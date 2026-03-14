package com.ditto.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Ditto API")
                .description(
                    """
                    Ditto 서비스 API 문서입니다.
                    
                    ## 인증
                    모든 API 요청에는 `X-API-Key` 헤더가 필요합니다.
                    API Key는 백엔드 담당자에게 발급받으세요.
                    
                    ```
                    X-API-Key: <발급받은 API Key>
                    ```
                    
                    Swagger UI에서 테스트하려면 우측 상단의 **Authorize** 버튼을 클릭하고 API Key를 입력하세요.
                    """.trimIndent()
                )
                .version("v1")
        )
        .components(
            Components().addSecuritySchemes(
                "ApiKey",
                SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .`in`(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("API 인증 키. 백엔드 담당자에게 발급받으세요.")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("ApiKey"))
}
