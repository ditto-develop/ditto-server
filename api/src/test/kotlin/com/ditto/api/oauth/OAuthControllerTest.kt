package com.ditto.api.oauth

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class OAuthControllerTest : RestDocsTest() {

    companion object {
        private val PROVIDER_DESCRIPTION =
            "소셜 로그인 제공자 (${SocialProvider.entries.joinToString(", ") { it.name }})"
    }

    @Test
    @DisplayName("소셜 로그인 페이지로 리다이렉트한다")
    fun login() {
        mockMvc.perform(get("/api/v1/users/social-login/{provider}", "KAKAO").withApiKey())
            .andExpect(status().isFound)
            .andExpect(header().exists("Location"))
            .andDo(
                document(
                    "oauth-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("소셜 로그인")
                            .description("소셜 로그인 제공자의 인가 페이지로 리다이렉트합니다.")
                            .pathParameters(
                                parameterWithName("provider").description(PROVIDER_DESCRIPTION),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("인가 코드로 로그인하고 JWT를 반환한다")
    fun callback() {
        mockMvc.perform(
            get("/api/v1/users/social-login/{provider}/callback", "KAKAO").withApiKey()
                .param("code", "test-auth-code"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andDo(
                document(
                    "oauth-callback",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("소셜 로그인 콜백")
                            .description("소셜 로그인 제공자로부터 받은 인가 코드로 JWT를 발급합니다.")
                            .pathParameters(
                                parameterWithName("provider").description(PROVIDER_DESCRIPTION),
                            )
                            .queryParameters(
                                parameterWithName("code").description("소셜 로그인 제공자로부터 받은 인가 코드"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.accessToken").description("JWT 액세스 토큰"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("지원하지 않는 provider로 로그인 요청 시 에러를 반환한다")
    fun loginWithUnsupportedProvider() {
        mockMvc.perform(get("/api/v1/users/social-login/{provider}", "google").withApiKey())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("1001"))
    }
}
