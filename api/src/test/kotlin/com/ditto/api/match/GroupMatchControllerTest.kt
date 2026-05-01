package com.ditto.api.match

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GroupMatchControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var groupMatchRepository: GroupMatchRepository

    @Autowired
    private lateinit var groupMatchDeclineRepository: GroupMatchDeclineRepository

    // JWT 토큰은 memberId=1 로 발급
    private val memberId = 1L
    private val quizSetId = 10L

    @Test
    @DisplayName("그룹 매칭에 참여한다")
    fun joinGroupMatch() {
        mockMvc.perform(
            post("/api/v1/matches/group/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quizSetId": $quizSetId}""")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(quizSetId))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.isActive").value(false))
    }

    @Test
    @DisplayName("이미 그룹 매칭에 참여했으면 409를 반환한다")
    fun joinGroupMatch_alreadyJoined() {
        // 먼저 참여
        mockMvc.perform(
            post("/api/v1/matches/group/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quizSetId": $quizSetId}""")
                .withApiKey()
                .withBearerToken(),
        )

        // 중복 참여
        mockMvc.perform(
            post("/api/v1/matches/group/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quizSetId": $quizSetId}""")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("5005"))
    }

    @Test
    @DisplayName("그룹 매칭을 거절한다")
    fun declineGroupMatch() {
        mockMvc.perform(
            post("/api/v1/matches/group/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quizSetId": $quizSetId}""")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    @DisplayName("이미 거절했으면 409를 반환한다")
    fun declineGroupMatch_alreadyDeclined() {
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        mockMvc.perform(
            post("/api/v1/matches/group/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quizSetId": $quizSetId}""")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("5006"))
    }
}
