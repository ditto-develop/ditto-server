package com.ditto.api.match

import com.ditto.api.match.controller.MatchCandidateController
import com.ditto.api.match.dto.Candidate
import com.ditto.api.match.dto.MatchCandidateResponse
import com.ditto.api.match.dto.ScoreSummary
import com.ditto.api.match.service.MatchCandidateService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate
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

class MatchCandidateControllerTest : ControllerUnitTest() {

    private val matchCandidateService: MatchCandidateService = mockk()

    override val controller = MatchCandidateController(matchCandidateService)

    @Test
    @DisplayName("1:1 매칭 추천 후보 목록을 조회한다")
    fun getMatchCandidates() {
        every { matchCandidateService.getMatchCandidates(any()) } returns MatchCandidateResponse(
            quizSetId = 10L,
            weekStartedOn = LocalDate.of(2026, 9, 14),
            year = 2026,
            month = 9,
            week = 3,
            matchingType = MatchingType.ONE_TO_ONE,
            algorithmVersion = "1.0",
            candidates = listOf(
                Candidate(
                    userId = 2L,
                    nickname = "디토",
                    gender = "FEMALE",
                    age = 27,
                    introduction = "한 단어로 표현하면 디토",
                    location = "seoul",
                    profileImageUrl = "f1",
                    matchRate = 87.5,
                    scoreBreakdown = ScoreSummary(
                        quizMatchRate = 87.5,
                        matchedQuestions = 7,
                        totalQuestions = 8,
                        reasons = listOf("전체 8문항 중 7문항이 일치했어요"),
                    ),
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/matches/1on1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSetId").value(10))
            .andExpect(jsonPath("$.data.weekStartedOn").value("2026-09-14"))
            .andExpect(jsonPath("$.data.candidates.length()").value(1))
            .andExpect(jsonPath("$.data.candidates[0].userId").value(2))
            .andDo(
                document(
                    "match-candidate-list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("1:1 매칭 추천 후보 목록 조회")
                            .description(
                                "회원이 이번 운영 주에 완주한 1:1 퀴즈셋의 추천 후보를 매칭 점수 내림차순으로 조회합니다. " +
                                    "대상 퀴즈셋은 서버가 결정하며, 응답의 quizSetId·weekStartedOn 으로 확인합니다. " +
                                    "이번 주에 1:1 퀴즈를 완주하지 않았으면 404(0004) 입니다 — 지난 주 후보는 내려가지 않습니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("후보가 속한 퀴즈 세트 ID"),
                                fieldWithPath("data.weekStartedOn").description("대상 퀴즈셋의 운영 주 시작일(월요일). FE 는 이 값을 GET /api/v1/system/state 의 weekStartedOn 과 대조한다"),
                                fieldWithPath("data.year").description("운영 주 연도 (weekStartedOn 파생 표시값)"),
                                fieldWithPath("data.month").description("운영 주 월 (weekStartedOn 파생 표시값)"),
                                fieldWithPath("data.week").description("운영 주 주차 (weekStartedOn 파생 표시값)"),
                                fieldWithPath("data.matchingType").description("매칭 타입 (ONE_TO_ONE / GROUP)"),
                                fieldWithPath("data.algorithmVersion").description("매칭 알고리즘 버전"),
                                fieldWithPath("data.candidates[]").description("추천 후보 목록 (매칭 점수 내림차순)"),
                                fieldWithPath("data.candidates[].userId").description("후보 회원 ID"),
                                fieldWithPath("data.candidates[].nickname").description("닉네임"),
                                fieldWithPath("data.candidates[].gender").description("성별 (MALE / FEMALE, 없으면 null)").optional(),
                                fieldWithPath("data.candidates[].age").description("나이 (없으면 null)").optional(),
                                fieldWithPath("data.candidates[].introduction").description("자기소개 (소개노트 한 줄 소개, 없으면 null)").optional(),
                                fieldWithPath("data.candidates[].location").description("사는 곳 코드"),
                                fieldWithPath("data.candidates[].profileImageUrl").description("프로필 이미지 (캐리커쳐)"),
                                fieldWithPath("data.candidates[].matchRate").description("매칭 점수 (0~100)"),
                                fieldWithPath("data.candidates[].scoreBreakdown").description("매칭 점수 상세"),
                                fieldWithPath("data.candidates[].scoreBreakdown.quizMatchRate").description("퀴즈 답변 일치율 (0~100)"),
                                fieldWithPath("data.candidates[].scoreBreakdown.matchedQuestions").description("일치한 문항 수"),
                                fieldWithPath("data.candidates[].scoreBreakdown.totalQuestions").description("전체 비교 문항 수"),
                                fieldWithPath("data.candidates[].scoreBreakdown.reasons[]").description("매칭 사유 문구"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
