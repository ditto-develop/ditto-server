package com.ditto.api.match

import com.ditto.api.match.controller.PersonalMatchController
import com.ditto.api.match.dto.PersonalMatchListResponse
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.dto.PersonalMatchResponse
import com.ditto.api.match.service.PersonalMatchService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class PersonalMatchControllerTest : ControllerUnitTest() {

    private val personalMatchService: PersonalMatchService = mockk()

    override val controller = PersonalMatchController(personalMatchService)

    private fun sampleResponse(
        id: Long = 1L,
        requesterId: Long = 1L,
        receiverId: Long = 2L,
        status: PersonalMatchStatus = PersonalMatchStatus.PENDING,
    ) = PersonalMatchResponse(
        id = id,
        quizSetId = 10L,
        requesterId = requesterId,
        receiverId = receiverId,
        status = status,
        createdAt = LocalDateTime.of(2026, 5, 1, 12, 0),
        respondedAt = null,
    )

    @Test
    @DisplayName("보낸/받은 1:1 매칭 요청 목록을 조회한다")
    fun getPersonalMatches() {
        every { personalMatchService.getPersonalMatches(any(), any()) } returns PersonalMatchListResponse(
            sent = listOf(sampleResponse(requesterId = 1L, receiverId = 2L)),
            received = listOf(sampleResponse(id = 2L, requesterId = 3L, receiverId = 1L)),
        )

        mockMvc.perform(
            get("/api/v1/matches/1on1").param("quizSetId", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sent.length()").value(1))
            .andExpect(jsonPath("$.data.received.length()").value(1))
            .andDo(
                document(
                    "personal-match-list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("1:1 매칭 요청 목록 조회")
                            .description("퀴즈셋에 대한 내가 보낸/받은 1:1 매칭 요청 목록을 조회합니다.")
                            .queryParameters(
                                parameterWithName("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.sent[]").description("내가 보낸 요청 목록"),
                                fieldWithPath("data.sent[].id").description("매칭 ID"),
                                fieldWithPath("data.sent[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.sent[].requesterId").description("요청자 ID"),
                                fieldWithPath("data.sent[].receiverId").description("수신자 ID"),
                                fieldWithPath("data.sent[].status").description("상태 (PENDING / ACCEPTED / REJECTED / CANCELLED / EXPIRED)"),
                                fieldWithPath("data.sent[].createdAt").description("요청 생성일시"),
                                fieldWithPath("data.sent[].respondedAt").description("응답 일시 (없으면 null)").optional(),
                                fieldWithPath("data.received[]").description("내가 받은 요청 목록"),
                                fieldWithPath("data.received[].id").description("매칭 ID"),
                                fieldWithPath("data.received[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.received[].requesterId").description("요청자 ID"),
                                fieldWithPath("data.received[].receiverId").description("수신자 ID"),
                                fieldWithPath("data.received[].status").description("상태"),
                                fieldWithPath("data.received[].createdAt").description("요청 생성일시"),
                                fieldWithPath("data.received[].respondedAt").description("응답 일시 (없으면 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("1:1 매칭을 요청한다")
    fun requestMatch() {
        every { personalMatchService.requestMatch(any(), any()) } returns sampleResponse()

        mockMvc.perform(
            post("/api/v1/matches/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PersonalMatchRequest(receiverId = 2L, quizSetId = 10L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andDo(
                document(
                    "personal-match-request",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("1:1 매칭 요청")
                            .description("상대방에게 1:1 매칭을 요청합니다.")
                            .requestFields(
                                fieldWithPath("receiverId").description("요청 대상 회원 ID"),
                                fieldWithPath("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("생성된 매칭 ID"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.requesterId").description("요청자 ID"),
                                fieldWithPath("data.receiverId").description("수신자 ID"),
                                fieldWithPath("data.status").description("매칭 상태 (PENDING)"),
                                fieldWithPath("data.createdAt").description("요청 생성일시"),
                                fieldWithPath("data.respondedAt").description("응답 일시 (최초 요청 시 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("1:1 매칭 요청을 수락한다")
    fun acceptMatch() {
        every { personalMatchService.acceptMatch(any(), any()) } returns
            sampleResponse(status = PersonalMatchStatus.ACCEPTED)

        mockMvc.perform(post("/api/v1/matches/request/{id}/accept", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
            .andDo(
                document(
                    "personal-match-accept",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("id").description("매칭 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("1:1 매칭 수락")
                            .description("받은 매칭 요청을 수락합니다. 수신자만 호출 가능합니다.")
                            .pathParameters(
                                parameterWithName("id").description("매칭 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("매칭 ID"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.requesterId").description("요청자 ID"),
                                fieldWithPath("data.receiverId").description("수신자 ID"),
                                fieldWithPath("data.status").description("변경된 매칭 상태 (ACCEPTED)"),
                                fieldWithPath("data.createdAt").description("요청 생성일시"),
                                fieldWithPath("data.respondedAt").description("응답 일시").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("1:1 매칭 요청을 거절한다")
    fun rejectMatch() {
        every { personalMatchService.rejectMatch(any(), any()) } returns
            sampleResponse(status = PersonalMatchStatus.REJECTED)

        mockMvc.perform(post("/api/v1/matches/request/{id}/reject", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andDo(
                document(
                    "personal-match-reject",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("id").description("매칭 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("1:1 매칭 거절")
                            .description("받은 매칭 요청을 거절합니다. 수신자만 호출 가능합니다.")
                            .pathParameters(
                                parameterWithName("id").description("매칭 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("매칭 ID"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.requesterId").description("요청자 ID"),
                                fieldWithPath("data.receiverId").description("수신자 ID"),
                                fieldWithPath("data.status").description("변경된 매칭 상태 (REJECTED)"),
                                fieldWithPath("data.createdAt").description("요청 생성일시"),
                                fieldWithPath("data.respondedAt").description("응답 일시").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
