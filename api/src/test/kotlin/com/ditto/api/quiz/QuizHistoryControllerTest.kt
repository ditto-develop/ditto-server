package com.ditto.api.quiz

import com.ditto.api.quiz.controller.QuizHistoryController
import com.ditto.api.quiz.dto.QuizHistoryAnswer
import com.ditto.api.quiz.dto.QuizHistoryChoice
import com.ditto.api.quiz.dto.QuizHistoryCurrentWeek
import com.ditto.api.quiz.dto.QuizHistoryDetailResponse
import com.ditto.api.quiz.dto.QuizHistoryPhase
import com.ditto.api.quiz.dto.QuizHistoryResponse
import com.ditto.api.quiz.dto.QuizHistoryResult
import com.ditto.api.quiz.dto.QuizHistorySummary
import com.ditto.api.quiz.dto.QuizHistoryWeek
import com.ditto.api.quiz.service.QuizHistoryService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.quiz.entity.MatchingType
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
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

class QuizHistoryControllerTest : ControllerUnitTest() {

    private val quizHistoryService: QuizHistoryService = mockk()
    private val serverTimeProvider: ServerTimeProvider = mockk {
        every { now() } returns LocalDateTime.of(2026, 11, 5, 9, 0)
    }

    override val controller = QuizHistoryController(quizHistoryService, serverTimeProvider)

    @Test
    @DisplayName("퀴즈 기록 탭을 조회한다")
    fun getHistory() {
        every { quizHistoryService.getHistory(any(), any()) } returns QuizHistoryResponse(
            summary = QuizHistorySummary(participatedWeeks = 2, answeredQuestions = 24, minorityCount = 3),
            currentWeek = QuizHistoryCurrentWeek(
                quizSetId = 42L,
                resultOpen = true,
                resultOpensAt = LocalDateTime.of(2026, 11, 5, 0, 0),
            ),
            weeks = listOf(
                QuizHistoryWeek(42L, LocalDate.of(2026, 11, 2), MatchingType.GROUP, "시간이 멈춘다면", minorityCount = 1),
                QuizHistoryWeek(41L, LocalDate.of(2026, 10, 26), MatchingType.ONE_TO_ONE, "커튼을 열면", minorityCount = 2),
            ),
        )

        mockMvc.perform(get("/api/v1/quiz-history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.summary.minorityCount").value(3))
            .andDo(
                document(
                    "quiz-history",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Quiz")
                            .summary("퀴즈 기록 탭")
                            .description(
                                "끝까지 푼(COMPLETED) 주만 최신순으로 줍니다. 소수 의견 수는 결과가 공개된(마감 뒤 첫 자정) 주만 셉니다. " +
                                    "이번 주를 완주하지 않았으면 currentWeek 는 null 이고, 참여하기 노출은 GET /quiz-sets/current-week 로 판단합니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.summary.participatedWeeks").description("끝까지 푼 주의 수"),
                                fieldWithPath("data.summary.answeredQuestions").description("끝까지 푼 주들의 답 수 합"),
                                fieldWithPath("data.summary.minorityCount").description("결과가 공개된 주에서 내 답이 소수 의견이었던 문항 수 합"),
                                fieldWithPath("data.currentWeek").description("이번 운영 주 퀴즈를 완주했을 때만. 아니면 null").optional(),
                                fieldWithPath("data.currentWeek.quizSetId").description("이번 주 퀴즈셋 ID"),
                                fieldWithPath("data.currentWeek.resultOpen").description("결과 공개 시각이 지났는지 (서버 시각 기준) — 탭 뱃지 근거"),
                                fieldWithPath("data.currentWeek.resultOpensAt").description("결과 공개 시각 (마감 뒤 첫 자정, 보통 목 00:00)"),
                                fieldWithPath("data.weeks[].quizSetId").description("퀴즈셋 ID"),
                                fieldWithPath("data.weeks[].weekStartedOn").description("운영 주 시작일 (월요일)"),
                                fieldWithPath("data.weeks[].matchingType").description("매칭 유형 (ONE_TO_ONE, GROUP)"),
                                fieldWithPath("data.weeks[].title").description("퀴즈셋 제목"),
                                fieldWithPath("data.weeks[].minorityCount").description("이 주 소수 의견 문항 수. 결과 공개 전이면 null").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("결과가 공개된 주차 상세를 조회한다")
    fun getClosedDetail() {
        every { quizHistoryService.getDetail(any(), 41L, any()) } returns QuizHistoryDetailResponse(
            quizSetId = 41L,
            title = "커튼을 열면",
            matchingType = MatchingType.ONE_TO_ONE,
            weekStartedOn = LocalDate.of(2026, 10, 26),
            phase = QuizHistoryPhase.CLOSED,
            resultOpensAt = LocalDateTime.of(2026, 10, 29, 0, 0),
            participantCount = 312L,
            answers = listOf(
                QuizHistoryAnswer(
                    quizId = 301L,
                    order = 1,
                    question = "커튼을 열면 보이는 색은?",
                    choices = listOf(
                        QuizHistoryChoice(choiceId = 1L, content = "하늘빛", percent = 82, minority = false),
                        QuizHistoryChoice(choiceId = 2L, content = "노을빛", percent = 18, minority = true),
                    ),
                    myChoiceId = 2L,
                ),
            ),
            result = QuizHistoryResult(overlapThreshold = 8, overlapCount = 23, minorityCount = 1),
        )

        mockMvc.perform(get("/api/v1/quiz-history/{quizSetId}", 41L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phase").value("CLOSED"))
            .andExpect(jsonPath("$.data.result.overlapThreshold").value(8))
            .andDo(
                document(
                    "quiz-history-detail",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("quizSetId").description("퀴즈셋 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Quiz")
                            .summary("퀴즈 기록 주차 상세")
                            .description(
                                "phase 로 두 화면을 가릅니다. IN_PROGRESS(결과 공개 전)에는 choices[].percent·minority 와 result 키 자체가 없습니다. " +
                                    "CLOSED 의 숫자는 마감 스냅샷(완주자 기준)입니다. 끝까지 풀지 않은 셋이면 404(4006).",
                            )
                            .pathParameters(parameterWithName("quizSetId").description("퀴즈셋 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("퀴즈셋 ID"),
                                fieldWithPath("data.title").description("퀴즈셋 제목"),
                                fieldWithPath("data.matchingType").description("매칭 유형 (ONE_TO_ONE, GROUP)"),
                                fieldWithPath("data.weekStartedOn").description("운영 주 시작일 (월요일)"),
                                fieldWithPath("data.phase").description("IN_PROGRESS(결과 공개 전) | CLOSED(결과 공개 후)"),
                                fieldWithPath("data.resultOpensAt").description("결과 공개 시각. IN_PROGRESS 카운트다운에 쓰고 0이 되면 재조회"),
                                fieldWithPath("data.participantCount").description("IN_PROGRESS: 홈과 같은 지금까지 완주자 수 / CLOSED: 최종 참여자 수"),
                                fieldWithPath("data.answers[].quizId").description("문항 ID"),
                                fieldWithPath("data.answers[].order").description("문항 순서"),
                                fieldWithPath("data.answers[].question").description("질문 (최신 문구)"),
                                fieldWithPath("data.answers[].choices[].choiceId").description("선택지 ID"),
                                fieldWithPath("data.answers[].choices[].content").description("선택지 문구"),
                                fieldWithPath("data.answers[].choices[].percent").description("CLOSED 만: 정수 %, 문항 안 합 100").optional(),
                                fieldWithPath("data.answers[].choices[].minority").description("CLOSED 만: 소수 의견 여부 (참여자 20명 이상에서 20% 이하)").optional(),
                                fieldWithPath("data.answers[].myChoiceId").description("내가 고른 선택지 ID"),
                                fieldWithPath("data.result").description("CLOSED 만").optional(),
                                fieldWithPath("data.result.overlapThreshold").description("'나와 {overlapThreshold}개 이상 답이 겹친 사람'의 기준 = ceil(문항 수 × 2/3)"),
                                fieldWithPath("data.result.overlapCount").description("그 기준 이상 답이 겹친 완주자 수 (본인 제외)"),
                                fieldWithPath("data.result.minorityCount").description("내 소수 의견 문항 수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("진행 중인 주차 상세는 비율·결과 키가 없다")
    fun getInProgressDetail() {
        every { quizHistoryService.getDetail(any(), 42L, any()) } returns QuizHistoryDetailResponse(
            quizSetId = 42L,
            title = "시간이 멈춘다면",
            matchingType = MatchingType.GROUP,
            weekStartedOn = LocalDate.of(2026, 11, 2),
            phase = QuizHistoryPhase.IN_PROGRESS,
            resultOpensAt = LocalDateTime.of(2026, 11, 5, 0, 0),
            participantCount = 52L,
            answers = listOf(
                QuizHistoryAnswer(
                    quizId = 401L,
                    order = 1,
                    question = "시간이 멈춘다면 그 시각은?",
                    choices = listOf(
                        QuizHistoryChoice(choiceId = 11L, content = "오후 3시", percent = null, minority = null),
                        QuizHistoryChoice(choiceId = 12L, content = "새벽 2시", percent = null, minority = null),
                    ),
                    myChoiceId = 12L,
                ),
            ),
            result = null,
        )

        mockMvc.perform(get("/api/v1/quiz-history/{quizSetId}", 42L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phase").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.result").doesNotExist())
            .andExpect(jsonPath("$.data.answers[0].choices[0].percent").doesNotExist())
    }
}
