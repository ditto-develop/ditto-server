package com.ditto.api.match

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PersonalMatchControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var personalMatchRepository: PersonalMatchRepository

    @Test
    @DisplayName("보낸/받은 1:1 매칭 요청 목록을 조회한다")
    fun getPersonalMatches() {
        val requesterId = 1L
        val quizSetId = 10L
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = requesterId, receiverId = 2L, quizSetId = quizSetId)
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 3L, receiverId = requesterId, quizSetId = quizSetId)
        )

        mockMvc.perform(
            get("/api/v1/matches/1on1")
                .param("quizSetId", quizSetId.toString())
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sent.length()").value(1))
            .andExpect(jsonPath("$.data.received.length()").value(1))
    }

    @Test
    @DisplayName("1:1 매칭을 요청한다")
    fun requestMatch() {
        val body = """{"receiverId": 2, "quizSetId": 10}"""

        mockMvc.perform(
            post("/api/v1/matches/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.requesterId").value(1))
            .andExpect(jsonPath("$.data.receiverId").value(2))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
    }

    @Test
    @DisplayName("1:1 매칭 요청을 수락한다")
    fun acceptMatch() {
        // JWT 토큰은 memberId=1 로 발급되므로 receiverId=1 로 설정
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 10L)
        )

        mockMvc.perform(
            post("/api/v1/matches/request/${match.id}/accept")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
    }

    @Test
    @DisplayName("1:1 매칭 요청을 거절한다")
    fun rejectMatch() {
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 10L)
        )

        mockMvc.perform(
            post("/api/v1/matches/request/${match.id}/reject")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
    }
}
