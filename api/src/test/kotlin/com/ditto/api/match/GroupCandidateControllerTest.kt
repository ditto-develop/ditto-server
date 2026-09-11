package com.ditto.api.match

import com.ditto.api.match.controller.GroupCandidateController
import com.ditto.api.match.dto.Candidate
import com.ditto.api.match.dto.CandidateGroup
import com.ditto.api.match.dto.GroupCandidateResponse
import com.ditto.api.match.dto.ScoreSummary
import com.ditto.api.match.service.GroupCandidateService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.match.entity.InvitationStatus
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GroupCandidateControllerTest : ControllerUnitTest() {

    private val groupCandidateService: GroupCandidateService = mockk()

    override val controller = GroupCandidateController(groupCandidateService)

    @Test
    @DisplayName("그룹 매칭 후보 그룹 목록을 조회한다")
    fun getGroupCandidates() {
        every { groupCandidateService.getGroupCandidates(any()) } returns GroupCandidateResponse(
            quizSetId = 10L,
            groups = listOf(
                CandidateGroup(
                    groupMatchId = 5L,
                    myStatus = InvitationStatus.PENDING,
                    isFormed = false,
                    averageMatchedQuestions = 8,
                    totalQuestions = 12,
                    members = listOf(
                        Candidate(
                            userId = 2L,
                            nickname = "댕이누나",
                            gender = "FEMALE",
                            age = 27,
                            introduction = "느긋한 집순이",
                            location = "seoul",
                            profileImageUrl = "f1",
                            matchRate = 66.7,
                            scoreBreakdown = ScoreSummary(
                                quizMatchRate = 66.7,
                                matchedQuestions = 8,
                                totalQuestions = 12,
                                reasons = listOf("전체 12문항 중 8문항이 일치했어요"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/matches/group"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(10))
            .andExpect(jsonPath("$.data.groups.length()").value(1))
            .andExpect(jsonPath("$.data.groups[0].groupMatchId").value(5))
            .andExpect(jsonPath("$.data.groups[0].averageMatchedQuestions").value(8))
            .andDo(
                document(
                    "group-candidate-list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("그룹 매칭 후보 그룹 목록 조회")
                            .description(
                                "회원이 최근 완료한 그룹 퀴즈셋에서 배정받은 후보 그룹을 그룹 점수 내림차순으로 조회합니다. " +
                                    "대상 퀴즈셋은 서버가 결정하며, 응답의 quizSetId 로 확인합니다. " +
                                    "이미 거절한 그룹은 목록에 포함되지 않습니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("후보 그룹이 속한 퀴즈 세트 ID"),
                                fieldWithPath("data.groups[]").description("후보 그룹 목록 (그룹 점수 내림차순)"),
                                fieldWithPath("data.groups[].groupMatchId").description("그룹 ID (수락·거절 요청에 사용)"),
                                fieldWithPath("data.groups[].myStatus").description("이 그룹에 대한 내 응답 상태 (PENDING / ACCEPTED)"),
                                fieldWithPath("data.groups[].isFormed").description("성사 여부. 성사되면 금요일에 채팅방이 열린다"),
                                fieldWithPath("data.groups[].averageMatchedQuestions")
                                    .description("나와 각 구성원의 일치 문항 수 평균 (반올림)"),
                                fieldWithPath("data.groups[].totalQuestions").description("전체 비교 문항 수"),
                                fieldWithPath("data.groups[].members[]").description("나를 제외한 구성원 (나와의 일치 문항 수 내림차순)"),
                                fieldWithPath("data.groups[].members[].userId").description("구성원 회원 ID"),
                                fieldWithPath("data.groups[].members[].nickname").description("닉네임"),
                                fieldWithPath("data.groups[].members[].gender").description("성별 (MALE / FEMALE, 없으면 null)").optional(),
                                fieldWithPath("data.groups[].members[].age").description("나이 (없으면 null)").optional(),
                                fieldWithPath("data.groups[].members[].introduction")
                                    .description("자기소개 (소개노트 한 줄 소개, 없으면 null)").optional(),
                                fieldWithPath("data.groups[].members[].location").description("사는 곳 코드"),
                                fieldWithPath("data.groups[].members[].profileImageUrl").description("프로필 이미지 (캐리커쳐)"),
                                fieldWithPath("data.groups[].members[].matchRate").description("나와의 매칭 점수 (0~100)"),
                                fieldWithPath("data.groups[].members[].scoreBreakdown").description("매칭 점수 상세"),
                                fieldWithPath("data.groups[].members[].scoreBreakdown.quizMatchRate")
                                    .description("퀴즈 답변 일치율 (0~100)"),
                                fieldWithPath("data.groups[].members[].scoreBreakdown.matchedQuestions").description("일치한 문항 수"),
                                fieldWithPath("data.groups[].members[].scoreBreakdown.totalQuestions").description("전체 비교 문항 수"),
                                fieldWithPath("data.groups[].members[].scoreBreakdown.reasons[]").description("매칭 사유 문구"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
