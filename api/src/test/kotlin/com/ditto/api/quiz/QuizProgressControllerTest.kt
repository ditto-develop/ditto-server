package com.ditto.api.quiz

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class QuizProgressControllerTest : RestDocsTest() {

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
}
