package com.ditto.api.match

import com.ditto.api.match.controller.GroupMatchController
import com.ditto.api.match.dto.GroupMatchAcceptResponse
import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.ControllerUnitTest
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GroupMatchControllerTest : ControllerUnitTest() {

    private val groupMatchService: GroupMatchService = mockk()

    override val controller = GroupMatchController(groupMatchService)

    @Test
    @DisplayName("후보 그룹 초대를 수락한다")
    fun accept() {
        every { groupMatchService.acceptGroupMatch(any(), any()) } returns GroupMatchAcceptResponse(
            groupMatchId = 5L,
            quizSetId = 10L,
            acceptedCount = 3,
            isFormed = true,
        )

        mockMvc.perform(post("/api/v1/matches/group/{groupMatchId}/accept", 5L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupMatchId").value(5))
            .andExpect(jsonPath("$.data.isFormed").value(true))
            .andDo(
                document(
                    "group-match-accept",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("그룹 초대 수락")
                            .description(
                                "후보 그룹 초대를 수락합니다. 되돌릴 수 없습니다. " +
                                    "수락 인원이 최소 인원(3명)에 닿으면 그룹이 성사되고 금요일에 채팅방이 열립니다. " +
                                    "수락하면 같은 퀴즈셋의 남은 초대는 자동으로 거절 처리됩니다.",
                            )
                            .pathParameters(parameterWithName("groupMatchId").description("수락할 그룹 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.groupMatchId").description("그룹 ID"),
                                fieldWithPath("data.quizSetId").description("그룹이 속한 퀴즈 세트 ID"),
                                fieldWithPath("data.acceptedCount").description("지금까지 수락한 인원"),
                                fieldWithPath("data.isFormed").description("성사 여부"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("후보 그룹 초대를 거절한다")
    fun decline() {
        justRun { groupMatchService.declineGroupMatch(any(), any()) }

        mockMvc.perform(post("/api/v1/matches/group/{groupMatchId}/decline", 5L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "group-match-decline",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("그룹 초대 거절")
                            .description("후보 그룹 초대를 거절합니다. 되돌릴 수 없으며 거절한 그룹은 후보 목록에서 사라집니다.")
                            .pathParameters(parameterWithName("groupMatchId").description("거절할 그룹 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 본문 없음").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
