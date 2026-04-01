package com.ditto.api.config.auth

import com.ditto.api.config.TestExceptionController
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestExceptionController::class)
class JwtAuthenticationFilterTest : RestDocsTest() {

    @Test
    @DisplayName("유효한 Bearer 토큰이면 MemberPrincipal을 받을 수 있다")
    fun validBearerToken() {
        val token = jwtTokenProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

        mockMvc.perform(
            get("/api/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.providerUserId").value("kakao-123"))
            .andExpect(jsonPath("$.data.provider").value("KAKAO"))
    }

    @Test
    @DisplayName("Bearer 토큰이 없으면 401과 에러 정보를 반환한다")
    fun noBearerToken() {
        mockMvc.perform(
            get("/api/test/me")
                .withApiKey(),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(401))
            .andExpect(jsonPath("$.error.code").value("0002"))
    }

    @Test
    @DisplayName("유효하지 않은 Bearer 토큰이면 401과 에러 정보를 반환한다")
    fun invalidBearerToken() {
        mockMvc.perform(
            get("/api/test/me")
                .withApiKey()
                .header("Authorization", "Bearer invalid-token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(401))
            .andExpect(jsonPath("$.error.code").value("0002"))
    }

    @Test
    @DisplayName("API Key 없이 Bearer 토큰만 보내면 401과 에러 정보를 반환한다")
    fun bearerTokenWithoutApiKey() {
        val token = jwtTokenProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

        mockMvc.perform(
            get("/api/test/me")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.statusCode").value(401))
            .andExpect(jsonPath("$.error.code").value("0002"))
    }
}
