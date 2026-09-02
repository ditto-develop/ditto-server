package com.ditto.api.user

import com.ditto.api.support.RestDocsTest
import com.ditto.api.user.dto.UpdateMyProfileRequest
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private val INTEREST_CODES = Interest.entries.joinToString(", ") { it.code }

class MyProfileControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var introNoteRepository: IntroNoteRepository

    @Autowired
    private lateinit var quizProgressRepository: QuizProgressRepository

    @Autowired
    private lateinit var chatRoomMemberRepository: ChatRoomMemberRepository

    @Autowired
    private lateinit var memberReviewRepository: MemberReviewRepository

    @Autowired
    private lateinit var reviewAnswerRepository: ReviewAnswerRepository

    @Test
    @DisplayName("내 프로필을 조회한다")
    fun getMyProfile() {
        val member = saveActiveMember()
        introNoteRepository.save(
            IntroNote.create(member.id, IntroQuestion.ONE_WORD, "주말마다 한강 산책하는 걸 좋아해요!"),
        )

        mockMvc.perform(
            get("/api/v1/users/me/profile")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.nickname").value("개굴개굴렌"))
            .andExpect(jsonPath("$.data.introduction").value("주말마다 한강 산책하는 걸 좋아해요!"))
            .andDo(
                document(
                    "my-profile-get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("내 프로필 조회")
                            .description(
                                "내 프로필을 조회합니다. 응답 형태는 타인 공개 프로필(GET /api/v1/users/{id}/profile)과 같습니다. " +
                                    "introduction은 소개노트 'one-word' 답변이며, 미작성이면 null입니다.",
                            )
                            .responseFields(*profileResponseFields())
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("캐리커쳐·관심사·한 줄 소개를 수정한다")
    fun updateMyProfile() {
        val member = saveActiveMember()
        val request = UpdateMyProfileRequest(
            introduction = "주말마다 한강 산책하는 걸 좋아해요!",
            profileImageUrl = "/assets/avatar/m3.png",
            interests = setOf("workout", "movie-drama", "exhibition"),
        )

        mockMvc.perform(
            patch("/api/v1/users/me/profile")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.profileImageUrl").value("/assets/avatar/m3.png"))
            .andExpect(jsonPath("$.data.introduction").value("주말마다 한강 산책하는 걸 좋아해요!"))
            .andDo(
                document(
                    "my-profile-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("내 프로필 수정")
                            .description(
                                "프로필 수정 화면에서 편집 가능한 항목만 수정합니다 — 캐리커쳐·관심사·한 줄 소개. " +
                                    "닉네임·성별·나이·사는곳·직업은 수정할 수 없습니다. " +
                                    "생략(null)한 항목은 변경하지 않습니다. " +
                                    "한 줄 소개는 소개노트 'one-word' 답변으로 저장됩니다.",
                            )
                            .requestFields(
                                fieldWithPath("introduction")
                                    .description("한 줄 소개 (최대 50자, 공백만 입력 불가). 생략 시 변경 없음")
                                    .optional(),
                                fieldWithPath("profileImageUrl")
                                    .description("캐리커쳐 경로. 생략 시 변경 없음")
                                    .optional(),
                                fieldWithPath("interests")
                                    .description("관심사 code 1~5개. 생략 시 변경 없음. 가능한 값: $INTEREST_CODES")
                                    .optional(),
                            )
                            .responseFields(*profileResponseFields())
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("한 줄 소개가 50자를 넘으면 거부한다")
    fun updateMyProfileRejectsTooLongIntroduction() {
        val member = saveActiveMember()
        val request = UpdateMyProfileRequest(introduction = "가".repeat(51))

        mockMvc.perform(
            patch("/api/v1/users/me/profile")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("한 줄 소개가 공백만이면 거부한다")
    fun updateMyProfileRejectsBlankIntroduction() {
        val member = saveActiveMember()
        val request = UpdateMyProfileRequest(introduction = "   ")

        mockMvc.perform(
            patch("/api/v1/users/me/profile")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("관심사를 6개 이상 보내면 거부한다")
    fun updateMyProfileRejectsTooManyInterests() {
        val member = saveActiveMember()
        val request = UpdateMyProfileRequest(
            interests = setOf("workout", "movie-drama", "exhibition", "performance", "photo", "reading"),
        )

        mockMvc.perform(
            patch("/api/v1/users/me/profile")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("내 통계를 조회한다")
    fun getMyStats() {
        val member = saveActiveMember()
        saveCompletedQuizProgress(member.id, quizSetId = 1L)
        saveCompletedQuizProgress(member.id, quizSetId = 2L)
        chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = 1L, memberId = member.id))
        saveReceivedAnswer(member.id, MeetingStatus.MET, rating = 5, comment = "대화가 편하고 좋았어요")

        mockMvc.perform(
            get("/api/v1/users/me/stats")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.participationWeeks").value(2))
            .andExpect(jsonPath("$.data.matchCount").value(1))
            .andExpect(jsonPath("$.data.meetingCount").value(1))
            .andDo(
                document(
                    "my-profile-stats",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("내 통계 조회")
                            .description(
                                "내 프로필의 '내 통계' 카드용 지표입니다. " +
                                    "참여 주차는 완주한 퀴즈 수, 매칭 성사는 개설된 채팅방 수, " +
                                    "만남 횟수는 상대방 평가에서 '만났어요'를 받은 개수입니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.participationWeeks").description("참여 주차 (완주한 퀴즈 수)"),
                                fieldWithPath("data.matchCount").description("매칭 성사 (개설된 채팅방 수)"),
                                fieldWithPath("data.meetingCount").description("만남 횟수 ('만났어요' 평가를 받은 개수)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("받은 평가가 3건 이상이면 평균·코멘트를 공개한다")
    fun getMyRatings() {
        val member = saveActiveMember()
        saveReceivedAnswer(member.id, MeetingStatus.MET, rating = 5, comment = "대화가 편하고 좋았어요")
        saveReceivedAnswer(member.id, MeetingStatus.MET, rating = 4, comment = "약속 시간 잘 지켜요")
        saveReceivedAnswer(member.id, MeetingStatus.NO_SHOW, rating = 3, comment = null)

        mockMvc.perform(
            get("/api/v1/users/me/ratings")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.averageScore").value(4.0))
            .andExpect(jsonPath("$.data.publicThreshold").value(3))
            .andExpect(jsonPath("$.data.noShowCount").value(1))
            .andDo(
                document(
                    "my-profile-ratings",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("받은 평가 조회")
                            .description(
                                "내가 받은 평가 요약입니다. 총 평가가 publicThreshold(3)건 미만이면 비공개로, " +
                                    "totalCount만 실제 값이고 평균·노쇼는 0, 코멘트는 빈 배열입니다. " +
                                    "별점 반올림과 코멘트 3개 노출은 클라이언트가 처리합니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.averageScore").description("평균 별점 (비공개 시 0)"),
                                fieldWithPath("data.totalCount").description("받은 평가 총 건수"),
                                fieldWithPath("data.publicThreshold").description("공개 기준 건수 (3)"),
                                fieldWithPath("data.noShowCount").description("노쇼 평가를 받은 횟수 (비공개 시 0)"),
                                fieldWithPath("data.ratings").description("평가 목록 최신순 (비공개 시 빈 배열)"),
                                fieldWithPath("data.ratings[].comment").description("한줄 코멘트 (미입력이면 null)").optional(),
                                fieldWithPath("data.ratings[].createdAt").description("평가 확정 일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("받은 평가가 3건 미만이면 총 건수만 내려준다")
    fun getMyRatingsBelowThreshold() {
        val member = saveActiveMember()
        saveReceivedAnswer(member.id, MeetingStatus.MET, rating = 5, comment = "좋았어요")

        mockMvc.perform(
            get("/api/v1/users/me/ratings")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.averageScore").value(0.0))
            .andExpect(jsonPath("$.data.noShowCount").value(0))
            .andExpect(jsonPath("$.data.ratings").isEmpty)
    }

    private var authorSequence = 1

    private fun saveActiveMember(): Member = memberRepository.save(
        MemberFixture.create(
            nickname = "개굴개굴렌",
            status = MemberStatus.ACTIVE,
            gender = Gender.MALE,
            age = 27,
            interests = setOf(Interest.WORKOUT),
            location = Location.SEOUL,
            job = Job.IT_TECH,
            caricature = "/assets/avatar/m1.png",
        ),
    )

    /** 완주(COMPLETED) 상태의 퀴즈 진행 하나를 만든다 — 총 1문항을 답한 것으로 둔다. */
    private fun saveCompletedQuizProgress(memberId: Long, quizSetId: Long) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
        progress.recordAnswer()
        quizProgressRepository.save(progress)
    }

    /** 다른 회원이 [reviewedMemberId]에게 남긴 확정 평가 하나를 만든다. */
    private fun saveReceivedAnswer(
        reviewedMemberId: Long,
        meetingStatus: MeetingStatus,
        rating: Int,
        comment: String?,
    ) {
        val author = memberRepository.save(
            MemberFixture.create(
                nickname = "평가자${authorSequence++}",
                email = "reviewer${authorSequence}@example.com",
                status = MemberStatus.ACTIVE,
            ),
        )
        val review = memberReviewRepository.save(MemberReviewFixture.create(authorMemberId = author.id))
        val answer = reviewAnswerRepository.save(
            ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = reviewedMemberId),
        )
        answer.answer(
            ReviewAnswerContent.of(meetingStatus, rating, comment),
            LocalDateTime.now(),
        )
        reviewAnswerRepository.save(answer)
    }

    private fun profileResponseFields() = arrayOf(
        fieldWithPath("success").description("성공 여부"),
        fieldWithPath("data.userId").description("회원 ID"),
        fieldWithPath("data.nickname").description("닉네임"),
        fieldWithPath("data.gender").description("성별 (MALE/FEMALE)").optional(),
        fieldWithPath("data.age").description("나이").optional(),
        fieldWithPath("data.introduction").description("한 줄 소개 (소개노트 one-word 답변, 미작성이면 null)").optional(),
        fieldWithPath("data.profileImageUrl").description("캐리커쳐 경로").optional(),
        fieldWithPath("data.location").description("사는 곳 code").optional(),
        fieldWithPath("data.occupation").description("직업 code").optional(),
        fieldWithPath("data.interests").description("관심사 code 목록"),
        // 항상 null 인 필드는 type 을 명시해야 스키마에 실린다(값으로 타입을 추론하지 못한다).
        fieldWithPath("data.rating").type(JsonFieldType.NUMBER)
            .description("받은 평가 평균 (공개 기준 3건 미만이면 null)").optional(),
        fieldWithPath("data.preferredMinAge").type(JsonFieldType.NUMBER)
            .description("선호 최소 나이 (미사용, null)").optional(),
        fieldWithPath("data.preferredMaxAge").type(JsonFieldType.NUMBER)
            .description("선호 최대 나이 (미사용, null)").optional(),
        fieldWithPath("error").description("에러 정보 (성공 시 null)"),
    )
}
