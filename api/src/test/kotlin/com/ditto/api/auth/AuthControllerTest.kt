package com.ditto.api.auth

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("리프레시 토큰으로 새 토큰 쌍을 발급한다")
    fun refresh() {
        val member = memberRepository.save(Member(nickname = "테스트유저"))
        val refreshToken = authService.createRefreshToken(member.id)
        val request = TokenRefreshRequest(refreshToken = refreshToken.token)

        mockMvc.perform(
            post("/api/v1/users/auth/refresh")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.refreshToken").exists())
            .andDo(
                document(
                    "token-refresh",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Auth")
                            .summary("토큰 갱신")
                            .description("리프레시 토큰으로 새로운 액세스 토큰과 리프레시 토큰을 발급합니다.")
                            .requestFields(
                                fieldWithPath("refreshToken").description("리프레시 토큰"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.accessToken").description("새 JWT 액세스 토큰"),
                                fieldWithPath("data.refreshToken").description("새 리프레시 토큰"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰이면 에러를 반환한다")
    fun refreshWithInvalidToken() {
        val request = TokenRefreshRequest(refreshToken = "invalid-token")

        mockMvc.perform(
            post("/api/v1/users/auth/refresh")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("2001"))
    }
}
