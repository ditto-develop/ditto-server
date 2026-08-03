package com.ditto.api.review

import com.ditto.api.review.controller.MemberReviewController
import com.ditto.api.review.dto.MemberReviewResponse
import com.ditto.api.review.dto.RematchResultResponse
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.api.review.dto.ReviewAnswerSubmitResponse
import com.ditto.api.review.dto.ReviewTargetResponse
import com.ditto.api.review.service.MemberReviewService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewProgressStatus
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
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class MemberReviewControllerTest : ControllerUnitTest() {

    private val memberReviewService: MemberReviewService = mockk()

    override val controller = MemberReviewController(memberReviewService)

    private fun answeredTarget() = ReviewTargetResponse(
        memberId = 2L,
        nickname = "댕이누나",
        gender = "FEMALE",
        age = 27,
        location = "seoul",
        profileImageUrl = "m1",
        meetingStatus = "CHAT_ONLY",
        rating = 4,
        comment = "친절하고 재밌어요",
        answeredAt = LocalDateTime.of(2026, 8, 3, 10, 0),
    )

    /** 아직 제출하지 않은 대상 — 답변 필드가 모두 null 로 나가는 것을 문서에 남긴다. */
    private fun unansweredTarget() = ReviewTargetResponse(
        memberId = 3L,
        nickname = "홍길동",
        gender = "MALE",
        age = 30,
        location = "gyeonggi",
        profileImageUrl = "m2",
        meetingStatus = null,
        rating = null,
        comment = null,
        answeredAt = null,
    )

    @Test
    @DisplayName("내 미완료 평가 목록을 조회한다")
    fun getMyPendingReviews() {
        every { memberReviewService.getMyPendingReviews(any()) } returns listOf(
            MemberReviewResponse(
                reviewId = 1L,
                matchType = ChatRoomType.GROUP,
                matchId = 7L,
                chatRoomId = 100L,
                availableAt = LocalDateTime.of(2026, 8, 2, 23, 59, 59),
                status = ReviewProgressStatus.IN_PROGRESS,
                answeredTargetCount = 1,
                totalTargetCount = 2,
                targets = listOf(answeredTarget(), unansweredTarget()),
            ),
        )

        mockMvc.perform(get("/api/v1/member-reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].reviewId").value(1L))
            .andExpect(jsonPath("$.data[0].answeredTargetCount").value(1))
            .andExpect(jsonPath("$.data[0].targets[1].answeredAt").doesNotExist())
            .andDo(
                document(
                    "member-reviews",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Review")
                            .summary("내 미완료 평가 목록 조회")
                            .description(
                                "아직 완료하지 않은 평가를 평가 가능해진 순서(오래된 순)로 반환한다. " +
                                    "대상 목록에는 내가 확정한 답변만 실리며, 상대가 나를 어떻게 평가했는지는 포함하지 않는다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data[].reviewId").description("평가 진행 단위 ID"),
                                fieldWithPath("data[].matchType").description("매칭 유형 (PERSONAL, GROUP)"),
                                fieldWithPath("data[].matchId").description("원본 매칭 ID"),
                                fieldWithPath("data[].chatRoomId").description("평가 문맥이 된 채팅방 ID"),
                                fieldWithPath("data[].availableAt").description("평가 가능 시각 (채팅 종료 시각)"),
                                fieldWithPath("data[].status")
                                    .description("진행 상태 (NOT_STARTED, IN_PROGRESS)"),
                                fieldWithPath("data[].answeredTargetCount").description("내가 확정한 대상 수"),
                                fieldWithPath("data[].totalTargetCount").description("전체 평가 대상 수"),
                                fieldWithPath("data[].targets[].memberId").description("평가 대상 회원 ID"),
                                fieldWithPath("data[].targets[].nickname")
                                    .description("대상 닉네임 (탈퇴 시 null)").optional(),
                                fieldWithPath("data[].targets[].gender")
                                    .description("대상 성별 (MALE, FEMALE)").optional(),
                                fieldWithPath("data[].targets[].age").description("대상 나이").optional(),
                                fieldWithPath("data[].targets[].location").description("대상 지역 code").optional(),
                                fieldWithPath("data[].targets[].profileImageUrl")
                                    .description("대상 프로필 이미지").optional(),
                                fieldWithPath("data[].targets[].meetingStatus")
                                    .description("내가 고른 만남 상태 (MET, APPOINTMENT_MADE, CHAT_ONLY, NO_SHOW). 미제출이면 null")
                                    .optional(),
                                fieldWithPath("data[].targets[].rating")
                                    .description("내가 준 별점 1~5. 미제출이면 null").optional(),
                                fieldWithPath("data[].targets[].comment")
                                    .description("내가 쓴 한줄 코멘트. 미제출이거나 미입력이면 null").optional(),
                                fieldWithPath("data[].targets[].answeredAt")
                                    .description("내 제출 시각. null 이면 아직 제출하지 않은 대상").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("대상 한 명에 대한 평가를 제출한다")
    fun submitAnswer() {
        val request = ReviewAnswerSubmitRequest(
            meetingStatus = MeetingStatus.CHAT_ONLY,
            rating = 4,
            comment = "친절하고 재밌어요",
            wantsOneToOneRematch = true,
        )
        every { memberReviewService.submitAnswer(any(), any(), any(), any()) } returns ReviewAnswerSubmitResponse(
            reviewId = 1L,
            status = ReviewProgressStatus.COMPLETED,
            answeredTargetCount = 2,
            totalTargetCount = 2,
            completedAt = LocalDateTime.of(2026, 8, 3, 10, 30),
            rematch = RematchResultResponse(
                matchedMemberId = 2L,
                matchedAt = LocalDateTime.of(2026, 8, 3, 10, 30),
            ),
        )

        mockMvc.perform(
            put("/api/v1/member-reviews/{reviewId}/targets/{memberId}", 1L, 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.rematch.matchedMemberId").value(2L))
            .andDo(
                document(
                    "member-review-submit",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Review")
                            .summary("대상별 평가 제출")
                            .description(
                                "대상 한 명에 대한 최종 제출이다. 수정·임시 저장 단계는 없으며, " +
                                    "마지막 대상을 제출하면 평가가 자동 완료돼 별도 완료 API가 없다. " +
                                    "같은 내용을 다시 보내면 현재 상태를 그대로 돌려주고, 다른 내용이면 거부한다.",
                            )
                            .pathParameters(
                                parameterWithName("reviewId").description("평가 ID"),
                                parameterWithName("memberId").description("평가 대상 회원 ID"),
                            )
                            .requestFields(
                                fieldWithPath("meetingStatus")
                                    .description("만남 상태 (MET, APPOINTMENT_MADE, CHAT_ONLY, NO_SHOW)"),
                                fieldWithPath("rating").description("별점 1~5 정수"),
                                fieldWithPath("comment")
                                    .description("한줄 코멘트. 최대 50자, 공백만 보내면 미입력 처리").optional(),
                                fieldWithPath("wantsOneToOneRematch")
                                    .description("1:1 재매칭 의사. 그룹 평가에서만 필수이며 1:1 평가에는 보낼 수 없다")
                                    .optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.reviewId").description("평가 ID"),
                                fieldWithPath("data.status")
                                    .description("제출 후 진행 상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.answeredTargetCount").description("확정한 대상 수"),
                                fieldWithPath("data.totalTargetCount").description("전체 평가 대상 수"),
                                fieldWithPath("data.completedAt")
                                    .description("전체 완료 시각. 미완료면 null").optional(),
                                fieldWithPath("data.rematch")
                                    .description("이번 제출로 상호 성사된 재매칭. 성사되지 않았으면 null — 이 값으로 상대의 선택 여부를 알 수는 없다")
                                    .optional(),
                                fieldWithPath("data.rematch.matchedMemberId")
                                    .description("성사된 상대 회원 ID").optional(),
                                fieldWithPath("data.rematch.matchedAt").description("성사 시각").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    // 만남 상태는 enum 으로 받으므로 잘못된 값은 서비스에 닿기 전 역직렬화에서 걸린다.
    @Test
    @DisplayName("지원하지 않는 만남 상태는 거부한다")
    fun submitAnswerWithUnknownMeetingStatus() {
        mockMvc.perform(
            put("/api/v1/member-reviews/{reviewId}/targets/{memberId}", 1L, 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"meetingStatus":"unknown","rating":4}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    @DisplayName("평가할 것이 없으면 빈 목록을 반환한다")
    fun getMyPendingReviewsEmpty() {
        every { memberReviewService.getMyPendingReviews(any()) } returns emptyList()

        mockMvc.perform(get("/api/v1/member-reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
    }
}
