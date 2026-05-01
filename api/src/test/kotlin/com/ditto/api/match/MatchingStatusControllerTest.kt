package com.ditto.api.match

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MatchingStatusControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var personalMatchRepository: PersonalMatchRepository

    @Autowired
    private lateinit var groupMatchRepository: GroupMatchRepository

    @Autowired
    private lateinit var groupMatchMemberRepository: GroupMatchMemberRepository

    @Autowired
    private lateinit var groupMatchDeclineRepository: GroupMatchDeclineRepository

    // JWT 토큰은 memberId=1 로 발급
    private val memberId = 1L
    private val quizSetId = 10L

    @Test
    @DisplayName("1:1 ACCEPTED 매칭이 있고 그룹 매칭에 참여한 상태를 조회한다")
    fun getMatchingStatus_withAllStatuses() {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = memberId, receiverId = 2L, quizSetId = quizSetId,
                status = PersonalMatchStatus.ACCEPTED,
            )
        )
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        mockMvc.perform(
            get("/api/v1/matching/status/$quizSetId")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(quizSetId))
            .andExpect(jsonPath("$.data.personalMatch.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.groupMatchStatus").value("JOINED"))
            .andExpect(jsonPath("$.data.groupMatchRoomId").value(room.id))
    }

    @Test
    @DisplayName("아무 매칭도 없을 때 NONE 상태를 반환한다")
    fun getMatchingStatus_noMatches() {
        mockMvc.perform(
            get("/api/v1/matching/status/$quizSetId")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.personalMatch").doesNotExist())
            .andExpect(jsonPath("$.data.groupMatchStatus").value("NONE"))
            .andExpect(jsonPath("$.data.groupMatchRoomId").doesNotExist())
    }

    @Test
    @DisplayName("그룹 매칭을 거절한 상태를 조회한다")
    fun getMatchingStatus_declined() {
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        mockMvc.perform(
            get("/api/v1/matching/status/$quizSetId")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupMatchStatus").value("DECLINED"))
    }
}
