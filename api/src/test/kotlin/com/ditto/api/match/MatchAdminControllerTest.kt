package com.ditto.api.match

import com.ditto.api.match.controller.MatchAdminController
import com.ditto.api.match.matching.MatchScore
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.CandidateGenerationSummary
import com.ditto.api.match.service.CandidateRowCounts
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.quiz.entity.MatchingType
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MatchAdminControllerTest : ControllerUnitTest() {

    private val matchmakingService: MatchmakingService = mockk()

    override val controller = MatchAdminController(matchmakingService)

    @Test
    @DisplayName("특정 퀴즈셋의 매칭 후보를 재생성하고 영향 받은 행·매칭된 후보를 돌려준다")
    fun regenerateMatching() {
        every { matchmakingService.generateMatchingCandidates(10L) } returns CandidateGenerationSummary(
            quizSetId = 10L,
            matchingType = MatchingType.ONE_TO_ONE,
            participantCount = 3,
            rowCounts = CandidateRowCounts(deletedCount = 4, savedCount = 2),
            matches = listOf(ScoredMatch.duo(1L, 2L, MatchScore(score = 100.0, matchedQuestionCount = 2, totalQuestionCount = 2))),
        )

        mockMvc.perform(post("/api/v1/admin/quiz-sets/{quizSetId}/matching/regenerate", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.participantCount").value(3))
            .andExpect(jsonPath("$.data.deletedRowCount").value(4))
            .andExpect(jsonPath("$.data.savedRowCount").value(2))
            .andExpect(jsonPath("$.data.candidates[0].memberIds[0]").value(1))
            .andDo(
                document(
                    "admin-match-regenerate",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("quizSetId").description("퀴즈 세트 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("[Admin] 퀴즈셋 매칭 재생성")
                            .description(
                                "특정 퀴즈셋의 매칭 후보를 재생성합니다(기존 후보 삭제 후 재계산). " +
                                    "마감·후보 존재 여부와 무관하게 실행됩니다. " +
                                    "이미 응답(수락·거절·성사)이 시작된 그룹 퀴즈셋은 5009(MATCH_CANDIDATES_ALREADY_RESPONDED)로 실패하며 " +
                                    "기존 후보를 건드리지 않습니다. 결과는 저장되지 않고 이 응답에만 실립니다.",
                            )
                            .pathParameters(parameterWithName("quizSetId").description("퀴즈 세트 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.matchingType").description("매칭 타입 (ONE_TO_ONE/GROUP)"),
                                fieldWithPath("data.participantCount").description("제외 정책을 통과해 후보 풀에 들어간 인원"),
                                fieldWithPath("data.deletedRowCount").description("지운 후보 행 수"),
                                fieldWithPath("data.savedRowCount").description("새로 쓴 후보 행 수"),
                                fieldWithPath("data.candidates[].memberIds").description("매칭 구성원 회원 ID (1:1은 2명, 그룹은 3~6명)"),
                                fieldWithPath("data.candidates[].score").description("매칭 점수 (0~100)"),
                                fieldWithPath("data.candidates[].matchedQuestionCount").description("같은 답을 고른 문항 수 (1:1만, 그룹은 null)").optional(),
                                fieldWithPath("data.candidates[].totalQuestionCount").description("비교한 전체 문항 수 (1:1만, 그룹은 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)").optional(),
                            )
                            .build(),
                    ),
                ),
            )

        verify { matchmakingService.generateMatchingCandidates(10L) }
    }
}
