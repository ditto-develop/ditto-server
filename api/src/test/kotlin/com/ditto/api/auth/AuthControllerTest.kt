package com.ditto.api.auth

import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.RefreshTokenCookieFactory
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Test
    @DisplayName("리프레시 토큰 쿠키로 새 액세스 토큰을 발급하고 refreshToken 쿠키를 재설정한다")
    fun refresh() {
        val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
        socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
        val refreshToken = authService.createRefreshToken(member.id)

        mockMvc.perform(
            post("/api/v1/users/auth/refresh")
                .withApiKey()
                .cookie(Cookie(REFRESH_TOKEN_COOKIE, refreshToken.token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(header().string("Set-Cookie", containsString("$REFRESH_TOKEN_COOKIE=")))
            .andDo(
                document(
                    "token-refresh",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Auth")
                            .summary("토큰 갱신")
                            .description("refreshToken 쿠키로 새 액세스 토큰을 발급하고 refreshToken 쿠키를 재설정합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.accessToken").description("새 JWT 액세스 토큰"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("리프레시 토큰 쿠키가 없으면 잘못된 요청 에러를 반환한다")
    fun refreshWithoutCookie() {
        mockMvc.perform(
            post("/api/v1/users/auth/refresh")
                .withApiKey(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰이면 에러를 반환한다")
    fun refreshWithInvalidToken() {
        mockMvc.perform(
            post("/api/v1/users/auth/refresh")
                .withApiKey()
                .cookie(Cookie(REFRESH_TOKEN_COOKIE, "invalid-token")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("2001"))
    }

    @Test
    @DisplayName("액세스 토큰으로 로그아웃하고 refreshToken 쿠키를 만료시킨다")
    fun logout() {
        val member = memberRepository.save(Member(nickname = "테스트유저").apply { activate() })
        socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "test-user"))
        authService.createRefreshToken(member.id)
        val accessToken = jwtTokenProvider.generateAccessToken(member.id)

        mockMvc.perform(
            post("/api/v1/users/auth/logout")
                .withApiKey()
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(header().string("Set-Cookie", containsString("$REFRESH_TOKEN_COOKIE=")))
            .andDo(
                document(
                    "logout",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Auth")
                            .summary("로그아웃")
                            .description("액세스 토큰의 회원 정보로 모든 리프레시 토큰을 삭제하고 refreshToken 쿠키를 만료시킵니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("데이터 (로그아웃 시 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    companion object {
        private const val REFRESH_TOKEN_COOKIE = RefreshTokenCookieFactory.REFRESH_TOKEN_COOKIE
    }
}
