package com.ditto.api.quiz

import com.ditto.api.quiz.controller.QuizProgressController
import com.ditto.api.quiz.dto.QuizChoiceResponse
import com.ditto.api.quiz.dto.QuizProgressResponse
import com.ditto.api.quiz.dto.QuizSetWithProgressResponse
import com.ditto.api.quiz.dto.QuizWithAnswerResponse
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.quiz.entity.QuizProgressStatus
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class QuizProgressControllerTest : ControllerUnitTest() {

    private val quizProgressService: QuizProgressService = mockk()

    override val controller = QuizProgressController(quizProgressService)

    @Test
    @DisplayName("퀴즈 답안을 제출한다")
    fun submitAnswer() {
        every { quizProgressService.submitAnswer(any(), any(), any()) } returns Unit
        val request = mapOf("quizId" to 1L, "choiceId" to 2L)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andDo(
                document(
                    "quiz-progress-submit-answer",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizProgress")
                            .summary("퀴즈 답안 제출")
                            .description("퀴즈에 대한 답안을 제출합니다. 이미 답변한 퀴즈에 재제출하면 선택지가 업데이트됩니다.")
                            .requestFields(
                                fieldWithPath("quizId").description("퀴즈 ID"),
                                fieldWithPath("choiceId").description("선택한 선택지 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("데이터 (답안 제출 시 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("퀴즈 진행률을 조회한다")
    fun getProgress() {
        every { quizProgressService.getProgress(any(), any()) } returns
            QuizProgressResponse(
                status = QuizProgressStatus.COMPLETED,
                quizSetId = 1L,
                quizSetTitle = "테스트 퀴즈셋",
                totalQuizzes = 1,
                answeredQuizzes = 1,
                participantCount = 1,
            )

        mockMvc.perform(get("/api/v1/quiz-progress/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andDo(
                document(
                    "quiz-progress-current",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizProgress")
                            .summary("퀴즈 진행률 조회")
                            .description("현재 주차의 퀴즈 진행 상태를 조회합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.status").description("진행 상태 (NOT_STARTED, IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.quizSetId").description("참여 중인 퀴즈 세트 ID").optional(),
                                fieldWithPath("data.quizSetTitle").description("참여 중인 퀴즈 세트 제목").optional(),
                                fieldWithPath("data.totalQuizzes").description("전체 퀴즈 수").optional(),
                                fieldWithPath("data.answeredQuizzes").description("답변한 퀴즈 수").optional(),
                                fieldWithPath("data.participantCount").description("완료한 참여자 수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("퀴즈셋의 문제와 답변을 조회한다")
    fun getQuizSetWithProgress() {
        val fixedTime = LocalDateTime.of(2026, 1, 1, 0, 0)
        every { quizProgressService.getQuizSetWithProgress(any(), any(), any()) } returns
            QuizSetWithProgressResponse(
                quizzes = listOf(
                    QuizWithAnswerResponse(
                        id = 1L,
                        question = "첫번째",
                        quizSetId = 1L,
                        choices = listOf(
                            QuizChoiceResponse(id = 1L, content = "A", order = 1),
                            QuizChoiceResponse(id = 2L, content = "B", order = 2),
                        ),
                        order = 1,
                        createdAt = fixedTime,
                        updatedAt = fixedTime,
                        userAnswer = 1L,
                    ),
                    QuizWithAnswerResponse(
                        id = 2L,
                        question = "두번째",
                        quizSetId = 1L,
                        choices = listOf(
                            QuizChoiceResponse(id = 3L, content = "C", order = 1),
                            QuizChoiceResponse(id = 4L, content = "D", order = 2),
                        ),
                        order = 2,
                        createdAt = fixedTime,
                        updatedAt = fixedTime,
                        userAnswer = null,
                    ),
                ),
                totalCount = 2,
            )

        mockMvc.perform(get("/api/v1/quiz-progress/quiz-sets/{id}", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.quizzes[0].userAnswer").value(1))
            .andExpect(jsonPath("$.data.quizzes[1].userAnswer").isEmpty)
            .andDo(
                document(
                    "quiz-progress-quiz-set-with-progress",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizProgress")
                            .summary("퀴즈셋 + 답변 조회")
                            .description("퀴즈셋의 문제 목록과 사용자의 답변을 함께 조회합니다. 이어풀기에 사용됩니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.totalCount").description("전체 퀴즈 수"),
                                fieldWithPath("data.quizzes[].id").description("퀴즈 ID"),
                                fieldWithPath("data.quizzes[].question").description("퀴즈 질문"),
                                fieldWithPath("data.quizzes[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.quizzes[].order").description("퀴즈 순서"),
                                fieldWithPath("data.quizzes[].createdAt").description("생성일시"),
                                fieldWithPath("data.quizzes[].updatedAt").description("수정일시"),
                                fieldWithPath("data.quizzes[].choices[].id").description("선택지 ID"),
                                fieldWithPath("data.quizzes[].choices[].content").description("선택지 내용"),
                                fieldWithPath("data.quizzes[].choices[].order").description("선택지 순서"),
                                fieldWithPath("data.quizzes[].userAnswer").description("사용자가 선택한 choiceId (미답변 시 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("비활성 퀴즈에 답안을 제출하면 에러를 반환한다")
    fun submitAnswerInactiveQuiz() {
        every { quizProgressService.submitAnswer(any(), any(), any()) } throws
            ErrorException(ErrorCode.QUIZ_NOT_IN_ACTIVE_SET)
        val request = mapOf("quizId" to 1L, "choiceId" to 2L)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("4001"))
    }

    @Test
    @DisplayName("유효하지 않은 선택지로 답안을 제출하면 에러를 반환한다")
    fun submitAnswerInvalidChoice() {
        every { quizProgressService.submitAnswer(any(), any(), any()) } throws
            ErrorException(ErrorCode.INVALID_CHOICE)
        val request = mapOf("quizId" to 1L, "choiceId" to 99999)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("4002"))
    }

    @Test
    @DisplayName("퀴즈 진행을 초기화한다")
    fun resetProgress() {
        every { quizProgressService.resetProgress(any(), any()) } returns Unit

        mockMvc.perform(post("/api/v1/quiz-progress/reset"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "quiz-progress-reset",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizProgress")
                            .summary("퀴즈 진행 초기화")
                            .description("이번 주차의 퀴즈 답변과 진행 상태를 모두 초기화합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("데이터 (초기화 시 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
