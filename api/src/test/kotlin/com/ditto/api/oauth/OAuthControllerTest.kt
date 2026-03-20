package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.MemberRepository
import com.ditto.domain.socialaccount.SocialAccountRepository
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(OAuthControllerTest.FakeOAuthConfig::class)
class OAuthControllerTest : RestDocsTest() {

    companion object {
        private val PROVIDER_DESCRIPTION =
            "소셜 로그인 제공자 (${SocialProvider.entries.joinToString(", ") { it.name.lowercase() }})"
    }

    @TestConfiguration
    class FakeOAuthConfig {
        @Bean
        @Primary
        fun fakeOAuthService(
            memberRepository: MemberRepository,
            socialAccountRepository: SocialAccountRepository,
            jwtTokenProvider: JwtTokenProvider,
        ): OAuthService {
            val fakeClient = object : OAuthClient {
                override fun getProvider() = SocialProvider.KAKAO
                override fun getAuthorizationUri() = "https://kauth.kakao.com/oauth/authorize"
                override fun getClientId() = "test-client-id"
                override fun getRedirectUri() = "http://localhost:8080/oauth/kakao/callback"
                override fun getAccessToken(code: String) = "test-access-token"
                override fun getUserInfo(accessToken: String) = OAuthUserInfo("12345", "테스트유저")
            }
            return OAuthService(
                oAuthClients = listOf(fakeClient),
                memberRepository = memberRepository,
                socialAccountRepository = socialAccountRepository,
                jwtTokenProvider = jwtTokenProvider,
            )
        }
    }

    @Test
    @DisplayName("소셜 로그인 페이지로 리다이렉트한다")
    fun login() {
        mockMvc.perform(get("/oauth/{provider}/login", "kakao"))
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
                            .build()
                    )
                )
            )
    }

    @Test
    @DisplayName("인가 코드로 로그인하고 JWT를 반환한다")
    fun callback() {
        mockMvc.perform(
            get("/oauth/{provider}/callback", "kakao")
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
                            .build()
                    )
                )
            )
    }
}
