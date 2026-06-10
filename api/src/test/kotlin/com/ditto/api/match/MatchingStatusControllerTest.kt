package com.ditto.api.match

import com.ditto.api.match.controller.MatchingStatusController
import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.api.match.dto.PersonalMatchResponse
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

    private fun sampleRequest(
        id: Long,
        requesterId: Long,
        receiverId: Long,
        status: PersonalMatchStatus,
        respondedAt: LocalDateTime? = null,
    ) = PersonalMatchResponse(
        id = id,
        quizSetId = 10L,
        requesterId = requesterId,
        receiverId = receiverId,
        status = status,
        createdAt = LocalDateTime.of(2026, 5, 1, 12, 0),
        respondedAt = respondedAt,
    )

    @Test
    @DisplayName("매칭 상태를 조회한다 — 보낸/받은 요청, 수락 매칭, 그룹 참여")
    fun getMatchingStatus() {
        every { matchingStatusService.getMatchingStatus(any(), any()) } returns MatchingStatusResponse(
            quizSetId = 10L,
            sentRequests = listOf(
                sampleRequest(
                    id = 1L, requesterId = 1L, receiverId = 2L,
                    status = PersonalMatchStatus.ACCEPTED,
                    respondedAt = LocalDateTime.of(2026, 5, 1, 12, 30),
                ),
            ),
            receivedRequests = listOf(
                sampleRequest(id = 2L, requesterId = 3L, receiverId = 1L, status = PersonalMatchStatus.PENDING),
            ),
            hasAcceptedMatch = true,
            acceptedMatchUserId = 2L,
            groupDeclined = false,
            groupJoined = true,
            groupJoinPending = false,
        )

        mockMvc.perform(get("/api/v1/matching/status/{quizSetId}", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(10))
            .andExpect(jsonPath("$.data.sentRequests.length()").value(1))
            .andExpect(jsonPath("$.data.receivedRequests.length()").value(1))
            .andExpect(jsonPath("$.data.hasAcceptedMatch").value(true))
            .andExpect(jsonPath("$.data.acceptedMatchUserId").value(2))
            .andExpect(jsonPath("$.data.groupJoined").value(true))
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
                                    "- **sentRequests / receivedRequests**: 내가 보낸/받은 1:1 매칭 요청 목록\n" +
                                    "- **hasAcceptedMatch / acceptedMatchUserId**: 수락된 1:1 매칭 보유 여부와 상대 ID\n" +
                                    "- **groupDeclined / groupJoined / groupJoinPending**: 그룹 매칭 거절 / 활성 방 참여 / 인원 대기 상태",
                            )
                            .pathParameters(
                                parameterWithName("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.sentRequests[]").description("내가 보낸 1:1 매칭 요청 목록"),
                                fieldWithPath("data.sentRequests[].id").description("매칭 ID"),
                                fieldWithPath("data.sentRequests[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.sentRequests[].requesterId").description("요청자 ID"),
                                fieldWithPath("data.sentRequests[].receiverId").description("수신자 ID"),
                                fieldWithPath("data.sentRequests[].status").description("상태 (PENDING / ACCEPTED / REJECTED / CANCELLED / EXPIRED)"),
                                fieldWithPath("data.sentRequests[].createdAt").description("요청 생성일시"),
                                fieldWithPath("data.sentRequests[].respondedAt").description("응답 일시 (없으면 null)").optional(),
                                fieldWithPath("data.receivedRequests[]").description("내가 받은 1:1 매칭 요청 목록"),
                                fieldWithPath("data.receivedRequests[].id").description("매칭 ID"),
                                fieldWithPath("data.receivedRequests[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.receivedRequests[].requesterId").description("요청자 ID"),
                                fieldWithPath("data.receivedRequests[].receiverId").description("수신자 ID"),
                                fieldWithPath("data.receivedRequests[].status").description("상태"),
                                fieldWithPath("data.receivedRequests[].createdAt").description("요청 생성일시"),
                                fieldWithPath("data.receivedRequests[].respondedAt").description("응답 일시 (없으면 null)").optional(),
                                fieldWithPath("data.hasAcceptedMatch").description("수락된 1:1 매칭 보유 여부"),
                                fieldWithPath("data.acceptedMatchUserId").description("수락된 매칭 상대 회원 ID (없으면 null)").optional(),
                                fieldWithPath("data.groupDeclined").description("그룹 매칭 거절 여부"),
                                fieldWithPath("data.groupJoined").description("활성화된(3명 이상) 그룹 방 참여 여부"),
                                fieldWithPath("data.groupJoinPending").description("그룹 방 참여했으나 인원 대기(비활성) 여부"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("매칭 이력이 없으면 빈 목록과 false 플래그를 반환한다")
    fun getMatchingStatus_empty() {
        every { matchingStatusService.getMatchingStatus(any(), any()) } returns MatchingStatusResponse(
            quizSetId = 10L,
            sentRequests = emptyList(),
            receivedRequests = emptyList(),
            hasAcceptedMatch = false,
            acceptedMatchUserId = null,
            groupDeclined = false,
            groupJoined = false,
            groupJoinPending = false,
        )

        mockMvc.perform(get("/api/v1/matching/status/{quizSetId}", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sentRequests.length()").value(0))
            .andExpect(jsonPath("$.data.hasAcceptedMatch").value(false))
            .andExpect(jsonPath("$.data.acceptedMatchUserId").doesNotExist())
            .andExpect(jsonPath("$.data.groupJoinPending").value(false))
    }
}
