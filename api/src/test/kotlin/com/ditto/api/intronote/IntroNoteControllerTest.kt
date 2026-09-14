package com.ditto.api.intronote

import com.ditto.api.intronote.dto.SaveIntroNoteRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// FE와 공유하는 소개노트 질문 code 목록. enum에서 생성해 문서가 항상 최신 상태를 유지한다.
private val INTRO_QUESTION_CODES = IntroQuestion.entries.joinToString(", ") { "${it.code}(${it.text})" }

class IntroNoteControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var introNoteRepository: IntroNoteRepository

    @Autowired
    private lateinit var personalMatchRepository: PersonalMatchRepository

    @Autowired
    private lateinit var matchCandidateRepository: MatchCandidateRepository

    @Autowired
    private lateinit var quizSetRepository: QuizSetRepository

    @Autowired
    private lateinit var quizProgressRepository: QuizProgressRepository

    @Test
    @DisplayName("소개노트 질문 하나의 답변을 저장한다")
    fun saveIntroNote() {
        val member = memberRepository.save(Member(nickname = "소개노트유저").apply { activate() })

        val request = SaveIntroNoteRequest(answer = "이어폰, 선크림, 카메라")

        mockMvc.perform(
            put("/api/v1/users/me/intro-notes/{questionCode}", "travel-items")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.completedCount").value(1))
            .andDo(
                document(
                    "intro-note-save",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("소개노트 답변 저장")
                            .description("질문 하나의 답변을 저장/수정합니다. 빈 문자열로 부분 저장이 가능합니다.")
                            .pathParameters(
                                parameterWithName("questionCode")
                                    .description("소개노트 질문 code. 가능한 값: $INTRO_QUESTION_CODES"),
                            )
                            .requestFields(
                                fieldWithPath("answer").description("답변 (빈 문자열 허용, 최대 500자)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.answers[].questionCode").description("질문 code (가능한 값: $INTRO_QUESTION_CODES)"),
                                fieldWithPath("data.answers[].question").description("질문 문구"),
                                fieldWithPath("data.answers[].answer").description("답변 (미작성 시 빈 문자열)"),
                                fieldWithPath("data.completedCount").description("작성 완료된 답변 수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("본인 소개노트를 조회한다")
    fun getMyIntroNotes() {
        val member = memberRepository.save(Member(nickname = "본인소개노트").apply { activate() })
        introNoteRepository.save(IntroNote.create(member.id, IntroQuestion.ONE_WORD, "도전"))

        mockMvc.perform(
            get("/api/v1/users/me/intro-notes")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.answers").isArray)
            .andDo(
                document(
                    "intro-note-me",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("본인 소개노트 조회")
                            .description("본인의 소개노트 전체를 고정 질문 순서대로 조회합니다. 미작성 질문은 빈 문자열입니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.answers[].questionCode").description("질문 code (가능한 값: $INTRO_QUESTION_CODES)"),
                                fieldWithPath("data.answers[].question").description("질문 문구"),
                                fieldWithPath("data.answers[].answer").description("답변 (미작성 시 빈 문자열)"),
                                fieldWithPath("data.completedCount").description("작성 완료된 답변 수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("매칭된 상대의 소개노트를 조회한다")
    fun getIntroNotes() {
        val viewer = memberRepository.save(Member(nickname = "조회자").apply { activate() })
        val target = memberRepository.save(Member(nickname = "대상자").apply { activate() })
        introNoteRepository.save(IntroNote.create(target.id, IntroQuestion.ONE_WORD, "열정"))
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = viewer.id,
                receiverId = target.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )

        mockMvc.perform(
            get("/api/v1/users/{id}/intro-notes", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.completedCount").value(1))
            .andDo(
                document(
                    "intro-note-other",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("타인 소개노트 조회")
                            .description(
                                """
                                상대의 소개노트를 조회합니다. 관계에 따라 공개 범위가 다릅니다.

                                - 매칭 성사(ACCEPTED)·같은 그룹 채팅 참여자: 고정 질문 **전체**
                                - 이번 주 매칭 후보(성사 전, `GET /api/v1/matches/1on1` 의 `candidates[].userId`):
                                  **미리보기 3문항만** — 상대가 작성한 답변 중 무작위 2문항 + `one-word`(항상 포함, 마지막).
                                  무작위 선택은 (조회자, 대상자) 기준으로 고정이라 재조회해도 같은 문항이 옵니다.
                                  기준 퀴즈셋은 후보 목록과 같아 다음 주 퀴즈셋을 완료하면 지난 주 후보는 닫힙니다.
                                - 그 외(차단 관계 포함): 403
                                """.trimIndent(),
                            )
                            .pathParameters(
                                parameterWithName("id").description("대상 사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.answers[].questionCode").description("질문 code (가능한 값: $INTRO_QUESTION_CODES)"),
                                fieldWithPath("data.answers[].question").description("질문 문구"),
                                fieldWithPath("data.answers[].answer").description("답변 (미작성 시 빈 문자열)"),
                                fieldWithPath("data.completedCount")
                                    .description("이 응답에 담긴 답변 중 작성된 수 (후보 미리보기면 3문항 기준)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("성사 전 매칭 후보의 소개노트는 미리보기 3문항만 조회된다")
    fun getIntroNotesAsMatchCandidate() {
        val viewer = memberRepository.save(Member(nickname = "후보조회자").apply { activate() })
        val target = memberRepository.save(Member(nickname = "이번주후보").apply { activate() })
        IntroQuestion.entries.forEach { question ->
            introNoteRepository.save(IntroNote.create(target.id, question, "${question.code} 답변"))
        }
        exposeAsCandidates(viewer.id, target.id)

        mockMvc.perform(
            get("/api/v1/users/{id}/intro-notes", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.answers.length()").value(3))
            .andExpect(jsonPath("$.data.answers[2].questionCode").value(IntroQuestion.ONE_WORD.code))
            .andExpect(jsonPath("$.data.completedCount").value(3))
    }

    /** 두 회원을 이번 주(=조회자가 최근 완료한 1:1 퀴즈셋) 후보로 서로 노출시킨다. */
    private fun exposeAsCandidates(viewerId: Long, targetId: Long) {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.ONE_TO_ONE))
        val progress = QuizProgressFixture.create(memberId = viewerId, quizSetId = quizSet.id, totalCount = 1)
        progress.recordAnswer() // NOT_STARTED -> COMPLETED
        quizProgressRepository.save(progress)
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = viewerId, otherMemberId = targetId, quizSetId = quizSet.id),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = targetId, otherMemberId = viewerId, quizSetId = quizSet.id),
        )
    }
}
