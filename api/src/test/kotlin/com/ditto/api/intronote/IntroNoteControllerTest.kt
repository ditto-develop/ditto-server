package com.ditto.api.intronote

import com.ditto.api.intronote.dto.SaveIntroNoteRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
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

class IntroNoteControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var introNoteRepository: IntroNoteRepository

    @Autowired
    private lateinit var personalMatchRepository: PersonalMatchRepository

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
                                parameterWithName("questionCode").description("소개노트 질문 code"),
                            )
                            .requestFields(
                                fieldWithPath("answer").description("답변 (빈 문자열 허용, 최대 500자)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.answers[].questionCode").description("질문 code"),
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
                                fieldWithPath("data.answers[].questionCode").description("질문 code"),
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
                            .description("매칭이 성사되었거나 같은 그룹 채팅에 참여한 상대의 소개노트를 조회합니다. 권한이 없으면 403.")
                            .pathParameters(
                                parameterWithName("id").description("대상 사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.answers[].questionCode").description("질문 code"),
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
}
