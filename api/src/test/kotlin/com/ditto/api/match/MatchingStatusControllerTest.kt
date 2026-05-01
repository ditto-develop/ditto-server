package com.ditto.api.match

import com.ditto.api.match.controller.MatchingStatusController
import com.ditto.api.match.dto.GroupMatchStatus
import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.api.match.dto.PersonalMatchSummary
import com.ditto.api.match.service.MatchingStatusService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class MatchingStatusControllerTest : ControllerUnitTest() {

    private val matchingStatusService: MatchingStatusService = mockk()

    override val controller = MatchingStatusController(matchingStatusService)

    @Test
    @DisplayName("매칭 상태를 조회한다 — 1:1 ACCEPTED, 그룹 JOINED")
    fun getMatchingStatus_withAllStatuses() {
        every { matchingStatusService.getMatchingStatus(any(), any()) } returns MatchingStatusResponse(
            quizSetId = 10L,
            personalMatch = PersonalMatchSummary(
                id = 1L,
                requesterId = 1L,
                receiverId = 2L,
                status = PersonalMatchStatus.ACCEPTED,
                createdAt = LocalDateTime.of(2026, 5, 1, 12, 0),
                respondedAt = LocalDateTime.of(2026, 5, 1, 12, 30),
            ),
            groupMatchStatus = GroupMatchStatus.JOINED,
            groupMatchRoomId = 5L,
        )

        mockMvc.perform(get("/api/v1/matching/status/{quizSetId}", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(10))
            .andExpect(jsonPath("$.data.personalMatch.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.groupMatchStatus").value("JOINED"))
            .andExpect(jsonPath("$.data.groupMatchRoomId").value(5))
            .andDo(
                document(
                    "matching-status",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("매칭 상태 조회")
                            .description(
                                "퀴즈셋에 대한 나의 매칭 현황을 조회합니다.\n\n" +
                                    "- **personalMatch**: ACCEPTED > PENDING 우선 반환. 없으면 null\n" +
                                    "- **groupMatchStatus**: DECLINED > JOINED > NONE 순으로 판단",
                            )
                            .pathParameters(
                                parameterWithName("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.personalMatch").description("1:1 매칭 정보 (없으면 null)").optional(),
                                fieldWithPath("data.personalMatch.id").description("매칭 ID").optional(),
                                fieldWithPath("data.personalMatch.requesterId").description("요청자 ID").optional(),
                                fieldWithPath("data.personalMatch.receiverId").description("수신자 ID").optional(),
                                fieldWithPath("data.personalMatch.status").description("매칭 상태 (PENDING / ACCEPTED / REJECTED / CANCELLED / EXPIRED)").optional(),
                                fieldWithPath("data.personalMatch.createdAt").description("요청 생성일시").optional(),
                                fieldWithPath("data.personalMatch.respondedAt").description("응답 일시 (없으면 null)").optional(),
                                fieldWithPath("data.groupMatchStatus").description("그룹 매칭 상태 (NONE / JOINED / DECLINED)"),
                                fieldWithPath("data.groupMatchRoomId").description("참여 중인 그룹 방 ID (JOINED 일 때만 존재)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("매칭이 없으면 personalMatch=null, groupMatchStatus=NONE 을 반환한다")
    fun getMatchingStatus_noMatches() {
        every { matchingStatusService.getMatchingStatus(any(), any()) } returns MatchingStatusResponse(
            quizSetId = 10L,
            personalMatch = null,
            groupMatchStatus = GroupMatchStatus.NONE,
            groupMatchRoomId = null,
        )

        mockMvc.perform(get("/api/v1/matching/status/{quizSetId}", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.personalMatch").doesNotExist())
            .andExpect(jsonPath("$.data.groupMatchStatus").value("NONE"))
            .andExpect(jsonPath("$.data.groupMatchRoomId").doesNotExist())
    }

    @Test
    @DisplayName("그룹 매칭을 거절한 경우 DECLINED 를 반환한다")
    fun getMatchingStatus_declined() {
        every { matchingStatusService.getMatchingStatus(any(), any()) } returns MatchingStatusResponse(
            quizSetId = 10L,
            personalMatch = null,
            groupMatchStatus = GroupMatchStatus.DECLINED,
            groupMatchRoomId = null,
        )

        mockMvc.perform(get("/api/v1/matching/status/{quizSetId}", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupMatchStatus").value("DECLINED"))
    }
}
