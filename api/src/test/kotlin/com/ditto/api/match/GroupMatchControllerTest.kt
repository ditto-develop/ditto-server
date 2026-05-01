package com.ditto.api.match

import com.ditto.api.match.controller.GroupMatchController
import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.dto.GroupMatchJoinResponse
import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GroupMatchControllerTest : ControllerUnitTest() {

    private val groupMatchService: GroupMatchService = mockk()

    override val controller = GroupMatchController(groupMatchService)

    @Test
    @DisplayName("그룹 매칭에 참여한다")
    fun joinGroupMatch() {
        every { groupMatchService.joinGroupMatch(any(), any()) } returns GroupMatchJoinResponse(
            roomId = 1L,
            quizSetId = 10L,
            participantCount = 1,
            isActive = false,
        )

        mockMvc.perform(
            post("/api/v1/matches/group/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(GroupMatchJoinRequest(quizSetId = 10L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roomId").value(1))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.isActive").value(false))
            .andDo(
                document(
                    "group-match-join",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("그룹 매칭 참여")
                            .description(
                                "그룹 매칭에 참여합니다.\n\n" +
                                    "- 참여 가능한 방이 있으면 기존 방에 배정됩니다.\n" +
                                    "- 참여 가능한 방이 없으면 새 방을 생성합니다.\n" +
                                    "- 참여자가 3명이 되면 방이 활성화(isActive=true)됩니다.",
                            )
                            .requestFields(
                                fieldWithPath("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.roomId").description("배정된 그룹 방 ID"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.participantCount").description("현재 참여자 수"),
                                fieldWithPath("data.isActive").description("방 활성화 여부 (참여자 3명 이상이면 true)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("이미 참여한 경우 에러를 반환한다")
    fun joinGroupMatch_alreadyJoined() {
        every { groupMatchService.joinGroupMatch(any(), any()) } throws WarnException(ErrorCode.ALREADY_JOINED_GROUP)

        mockMvc.perform(
            post("/api/v1/matches/group/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(GroupMatchJoinRequest(quizSetId = 10L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("5005"))
    }

    @Test
    @DisplayName("그룹 매칭을 거절한다")
    fun declineGroupMatch() {
        every { groupMatchService.declineGroupMatch(any(), any()) } returns Unit

        mockMvc.perform(
            post("/api/v1/matches/group/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(GroupMatchDeclineRequest(quizSetId = 10L))),
        )
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
                            .summary("그룹 매칭 거절")
                            .description("그룹 매칭 참여를 거절합니다. 한 퀴즈셋당 한 번만 거절할 수 있습니다.")
                            .requestFields(
                                fieldWithPath("quizSetId").description("퀴즈 세트 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("데이터 (null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("이미 거절한 경우 에러를 반환한다")
    fun declineGroupMatch_alreadyDeclined() {
        every { groupMatchService.declineGroupMatch(any(), any()) } throws WarnException(ErrorCode.ALREADY_DECLINED_GROUP)

        mockMvc.perform(
            post("/api/v1/matches/group/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(GroupMatchDeclineRequest(quizSetId = 10L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("5006"))
    }
}
