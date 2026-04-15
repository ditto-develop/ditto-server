package com.ditto.api.quiz

import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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

class QuizProgressControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var quizProgressService: QuizProgressService

    @Autowired
    private lateinit var quizSetRepository: QuizSetRepository

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var quizChoiceRepository: QuizChoiceRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    private val now = LocalDateTime.now()

    private fun setupAuthenticatedMember() {
        val member = memberRepository.save(Member(nickname = "테스트유저"))
        socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "test-user"))
    }

    @Test
    @DisplayName("퀴즈 답안을 제출한다")
    fun submitAnswer() {
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
        val request = mapOf("quizId" to quiz.id, "choiceId" to choice.id)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .withApiKey()
                .withBearerToken()
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
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

        quizProgressService.submitAnswer(
            com.ditto.api.config.auth.MemberPrincipal("test-user", SocialProvider.KAKAO),
            SubmitAnswerRequest(quiz.id, choice.id),
            now,
        )

        mockMvc.perform(
            get("/api/v1/quiz-progress/current")
                .withApiKey()
                .withBearerToken(),
        )
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
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "첫번째", displayOrder = 1))
        val quiz2 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
        val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "A", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "B", displayOrder = 2))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "C", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "D", displayOrder = 2))

        quizProgressService.submitAnswer(
            com.ditto.api.config.auth.MemberPrincipal("test-user", SocialProvider.KAKAO),
            SubmitAnswerRequest(quiz1.id, choice1.id),
            now,
        )

        mockMvc.perform(
            get("/api/v1/quiz-progress/quiz-sets/{id}", quizSet.id)
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.quizzes[0].userAnswer").value(choice1.id.toInt()))
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
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
        val request = mapOf("quizId" to quiz.id, "choiceId" to choice.id)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .withApiKey()
                .withBearerToken()
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
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
        val request = mapOf("quizId" to quiz.id, "choiceId" to 99999)

        mockMvc.perform(
            post("/api/v1/quiz-progress/answers")
                .withApiKey()
                .withBearerToken()
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
        setupAuthenticatedMember()
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

        quizProgressService.submitAnswer(
            com.ditto.api.config.auth.MemberPrincipal("test-user", SocialProvider.KAKAO),
            SubmitAnswerRequest(quiz.id, choice.id),
            now,
        )

        mockMvc.perform(
            post("/api/v1/quiz-progress/reset")
                .withApiKey()
                .withBearerToken(),
        )
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
