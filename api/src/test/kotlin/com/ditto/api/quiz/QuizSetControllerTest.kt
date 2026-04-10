package com.ditto.api.quiz

import com.ditto.api.support.RestDocsTest
import com.ditto.api.support.TestClockConfig
import org.springframework.context.annotation.Import
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestClockConfig::class)
class QuizSetControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var quizSetRepository: QuizSetRepository

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var quizChoiceRepository: QuizChoiceRepository

    private val fixedTime = TestClockConfig.FIXED_TIME

    @Test
    @DisplayName("이번 주차 퀴즈 세트를 조회한다")
    fun getCurrentWeek() {
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = fixedTime.minusDays(1),
                endDate = fixedTime.plusDays(1),
            ),
        )
        val quiz = quizRepository.save(
            QuizFixture.create(quizSetId = quizSet.id, question = "짜장면 vs 짬뽕?", displayOrder = 1),
        )
        quizChoiceRepository.save(
            QuizChoiceFixture.create(quizId = quiz.id, content = "짜장면", displayOrder = 1),
        )
        quizChoiceRepository.save(
            QuizChoiceFixture.create(quizId = quiz.id, content = "짬뽕", displayOrder = 2),
        )

        mockMvc.perform(
            get("/api/v1/quiz-sets/current-week")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.quizSets").isArray)
            .andExpect(jsonPath("$.data.quizSets[0].quizzes[0].choices").isArray)
            .andExpect(jsonPath("$.data.quizSets[0].quizzes[0].choices.length()").value(2))
            .andDo(
                document(
                    "quiz-set-current-week",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizSet")
                            .summary("이번 주차 퀴즈 세트 조회")
                            .description("현재 활성화된 이번 주차의 퀴즈 세트를 퀴즈 목록과 함께 조회합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.year").description("년도"),
                                fieldWithPath("data.month").description("월"),
                                fieldWithPath("data.week").description("주차"),
                                fieldWithPath("data.quizSets[].id").description("퀴즈 세트 ID"),
                                fieldWithPath("data.quizSets[].year").description("년도"),
                                fieldWithPath("data.quizSets[].month").description("월"),
                                fieldWithPath("data.quizSets[].week").description("주차"),
                                fieldWithPath("data.quizSets[].category").description("카테고리"),
                                fieldWithPath("data.quizSets[].title").description("퀴즈 세트 제목"),
                                fieldWithPath("data.quizSets[].description").description("퀴즈 세트 설명"),
                                fieldWithPath("data.quizSets[].startDate").description("시작일시"),
                                fieldWithPath("data.quizSets[].endDate").description("종료일시"),
                                fieldWithPath("data.quizSets[].isActive").description("활성화 여부"),
                                fieldWithPath("data.quizSets[].matchingType").description("매칭 타입 (ONE_TO_ONE, GROUP)"),
                                fieldWithPath("data.quizSets[].quizzes[].id").description("퀴즈 ID"),
                                fieldWithPath("data.quizSets[].quizzes[].question").description("퀴즈 질문"),
                                fieldWithPath("data.quizSets[].quizzes[].quizSetId").description("퀴즈 세트 ID"),
                                fieldWithPath("data.quizSets[].quizzes[].order").description("퀴즈 순서"),
                                fieldWithPath("data.quizSets[].quizzes[].createdAt").description("생성일시"),
                                fieldWithPath("data.quizSets[].quizzes[].updatedAt").description("수정일시"),
                                fieldWithPath("data.quizSets[].quizzes[].choices[].id").description("���택지 ID"),
                                fieldWithPath("data.quizSets[].quizzes[].choices[].content").description("선택지 내용"),
                                fieldWithPath("data.quizSets[].quizzes[].choices[].order").description("선택지 순서"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("퀴즈 세트를 단건 조회한다")
    fun getQuizSet() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())

        mockMvc.perform(
            get("/api/v1/quiz-sets/{id}", quizSet.id)
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(quizSet.id))
            .andExpect(jsonPath("$.data.title").value(quizSet.title))
            .andDo(
                document(
                    "quiz-set-get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("QuizSet")
                            .summary("퀴즈 세트 단건 조회")
                            .description("ID로 퀴즈 세트 메타데이터를 조회합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("퀴즈 세트 ID"),
                                fieldWithPath("data.year").description("년도"),
                                fieldWithPath("data.month").description("월"),
                                fieldWithPath("data.week").description("주차"),
                                fieldWithPath("data.category").description("카테고리"),
                                fieldWithPath("data.title").description("퀴즈 세트 제목"),
                                fieldWithPath("data.description").description("퀴즈 세트 설명"),
                                fieldWithPath("data.startDate").description("시작일시"),
                                fieldWithPath("data.endDate").description("종료일시"),
                                fieldWithPath("data.isActive").description("활성화 여부"),
                                fieldWithPath("data.matchingType").description("매칭 타입 (ONE_TO_ONE, GROUP)"),
                                fieldWithPath("data.createdAt").description("생성일시"),
                                fieldWithPath("data.updatedAt").description("수정일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("존재하지 않는 퀴즈 세트를 조회하면 에러를 반환한다")
    fun getQuizSetNotFound() {
        mockMvc.perform(
            get("/api/v1/quiz-sets/{id}", 99999)
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0004"))
    }
}
