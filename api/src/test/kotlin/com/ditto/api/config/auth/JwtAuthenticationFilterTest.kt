package com.ditto.api.config.auth

import com.ditto.api.config.TestExceptionController
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestExceptionController::class)
class JwtAuthenticationFilterTest : RestDocsTest() {

    @Test
    @DisplayName("유효한 Bearer 토큰이고 ACTIVE 회원이면 MemberPrincipal을 받을 수 있다")
    fun validBearerToken() {
        val member = memberRepository.save(Member(nickname = "활성유저").apply { activate() })
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            get("/api/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberId").value(member.id))
    }

    @Test
    @DisplayName("PENDING 회원이 보호 API에 접근하면 403과 에러 정보를 반환한다")
    fun pendingMemberForbidden() {
        val member = memberRepository.save(Member(nickname = "대기유저"))
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            get("/api/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("3001"))
    }

    @Test
    @DisplayName("PENDING 회원은 로그아웃 경로도 차단된다(403)")
    fun pendingMemberCannotLogout() {
        val member = memberRepository.save(Member(nickname = "대기유저-logout"))
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            post("/api/v1/users/auth/logout")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("3001"))
    }

    @Test
    @DisplayName("ADMIN 회원은 admin 경로에 접근할 수 있다")
    fun adminPathWithAdminMember() {
        val member = memberRepository.save(Member(nickname = "관리자", role = MemberRole.ADMIN).apply { activate() })
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            get("/api/v1/admin/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberId").value(member.id))
    }

    @Test
    @DisplayName("USER 회원이 admin 경로에 접근하면 403과 에러 정보를 반환한다")
    fun adminPathWithUserMember() {
        val member = memberRepository.save(Member(nickname = "일반유저").apply { activate() })
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            get("/api/v1/admin/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0003"))
    }

    @Test
    @DisplayName("USER 회원이 매칭 재생성 admin API를 호출하면 403과 에러 정보를 반환한다")
    fun adminMatchingApiWithUserMember() {
        val member = memberRepository.save(Member(nickname = "일반유저-매칭").apply { activate() })
        val token = jwtTokenProvider.generateAccessToken(member.id, member.role)

        mockMvc.perform(
            post("/api/v1/admin/quiz-sets/1/matching/regenerate")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0003"))
    }

    @Test
    @DisplayName("토큰은 유효하지만 존재하지 않는 회원이면 401과 에러 정보를 반환한다")
    fun unknownMember() {
        val token = jwtTokenProvider.generateAccessToken(99999L, MemberRole.USER)

        mockMvc.perform(
            get("/api/test/me")
                .withApiKey()
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("0002"))
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
        val token = jwtTokenProvider.generateAccessToken(1L, MemberRole.USER)

        mockMvc.perform(
            get("/api/test/me")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.statusCode").value(401))
            .andExpect(jsonPath("$.error.code").value("0002"))
    }
}
