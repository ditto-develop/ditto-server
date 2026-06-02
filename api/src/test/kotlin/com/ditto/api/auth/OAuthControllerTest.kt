package com.ditto.api.auth

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
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
    @DisplayName("콜백은 accessToken·signupRequired를 쿼리로, refreshToken을 HttpOnly 쿠키로 전달한다")
    fun callback() {
        mockMvc.perform(
            get("/api/v1/users/social-login/{provider}/callback", "KAKAO").withApiKey()
                .param("code", "test-auth-code"),
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", startsWith("http://localhost:3000/auth/callback")))
            .andExpect(header().string("Location", containsString("accessToken")))
            .andExpect(header().string("Location", containsString("signupRequired")))
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andDo(
                document(
                    "oauth-callback",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("소셜 로그인 콜백")
                            .description(
                                "소셜 로그인 인가 코드를 받아 토큰을 발급한 뒤 프론트 콜백 페이지로 리다이렉트한다. " +
                                    "accessToken과 회원가입 필요 여부(signupRequired)는 쿼리 파라미터로, " +
                                    "refreshToken은 HttpOnly 쿠키로 전달된다.",
                            )
                            .pathParameters(
                                parameterWithName("provider").description(PROVIDER_DESCRIPTION),
                            )
                            .queryParameters(
                                parameterWithName("code").description("소셜 로그인 제공자로부터 받은 인가 코드"),
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
