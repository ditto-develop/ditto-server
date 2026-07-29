package com.ditto.api.review

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.api.review.service.MemberReviewService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

class ReviewAnswerSubmitServiceTest(
    private val memberReviewService: MemberReviewService,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val rematchRepository: RematchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val author = 1L
    val counterpart = 2L
    val thirdMember = 3L
    val sourceMatchId = 7L
    val chatRoomId = 100L
    val endedAt = LocalDateTime.of(2026, 8, 2, 23, 59, 59)

    fun endedChatRoom(
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        participantIds: List<Long> = listOf(author, counterpart),
    ) = EndedChatRoom(
        chatRoomId = chatRoomId,
        matchType = matchType,
        matchId = sourceMatchId,
        quizSetId = 3L,
        weekStartedOn = LocalDate.of(2026, 7, 27),
        participantIds = participantIds,
        endedAt = endedAt,
    )

    fun openPersonalReview(): Long {
        memberReviewService.createReviews(endedChatRoom())
        return memberReviewRepository.findByChatRoomIdAndAuthorMemberId(chatRoomId, author)!!.id
    }

    /** 그룹 평가와 함께 참여자 쌍의 재매칭 행까지 만든다 — 운영에서는 그룹 종료 어댑터(I1G)가 함께 만든다. */
    fun openGroupReviewWithRematchPairs(): Long {
        memberReviewService.createReviews(
            endedChatRoom(ChatRoomType.GROUP, listOf(author, counterpart, thirdMember)),
        )
        listOf(counterpart, thirdMember).forEach { target ->
            rematchRepository.save(
                RematchFixture.create(
                    sourceGroupMatchId = sourceMatchId,
                    memberIdA = author,
                    memberIdB = target,
                ),
            )
        }
        return memberReviewRepository.findByChatRoomIdAndAuthorMemberId(chatRoomId, author)!!.id
    }

    fun submitRequest(
        meetingStatus: MeetingStatus = MeetingStatus.CHAT_ONLY,
        rating: Int = 4,
        comment: String? = "친절하고 재밌어요",
        wantsOneToOneRematch: Boolean? = null,
    ) = ReviewAnswerSubmitRequest(meetingStatus, rating, comment, wantsOneToOneRematch)

    "대상별 제출" - {
        "제출하면 답변이 확정되고 진행률이 오른다" {
            val reviewId = openGroupReviewWithRematchPairs()

            val response = memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = false),
            )

            response.reviewId shouldBe reviewId
            response.status shouldBe ReviewProgressStatus.IN_PROGRESS
            response.answeredTargetCount shouldBe 1
            response.totalTargetCount shouldBe 2
            response.completedAt shouldBe null

            val answer = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(reviewId).first { it.reviewedMemberId == counterpart }
            answer.meetingStatus shouldBe MeetingStatus.CHAT_ONLY
            answer.rating shouldBe 4
            answer.comment shouldBe "친절하고 재밌어요"
            answer.answeredAt shouldNotBe null
        }

        "마지막 대상을 제출하면 평가가 자동 완료된다" {
            val reviewId = openGroupReviewWithRematchPairs()
            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = false))

            val response = memberReviewService.submitAnswer(
                author, reviewId, thirdMember, submitRequest(wantsOneToOneRematch = false),
            )

            response.status shouldBe ReviewProgressStatus.COMPLETED
            response.answeredTargetCount shouldBe 2
            response.completedAt shouldNotBe null
        }

        "대상이 하나뿐인 1:1은 첫 제출로 바로 완료된다" {
            val reviewId = openPersonalReview()

            val response = memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest())

            response.status shouldBe ReviewProgressStatus.COMPLETED
            response.completedAt shouldNotBe null
        }

        "공백만 있는 코멘트는 미입력으로 저장된다" {
            val reviewId = openPersonalReview()

            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(comment = "   "))

            reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(reviewId).first { it.reviewedMemberId == counterpart }
                .comment shouldBe null
        }
    }

    "재제출" - {
        "같은 내용을 다시 보내면 현재 상태를 그대로 돌려준다" {
            val reviewId = openPersonalReview()
            val request = submitRequest()
            val first = memberReviewService.submitAnswer(author, reviewId, counterpart, request)

            val again = memberReviewService.submitAnswer(author, reviewId, counterpart, request)

            again shouldBe first
        }

        "코멘트만 공백 차이가 나도 같은 내용으로 본다" {
            val reviewId = openPersonalReview()
            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(comment = "좋았어요"))

            val again = memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(comment = "  좋았어요  "),
            )

            again.status shouldBe ReviewProgressStatus.COMPLETED
        }

        // 재매칭 의사만 바뀐 재제출이 조용히 성공하면, 사용자는 거절했다고 믿는 상대와 성사될 수 있다.
        "재매칭 의사만 바꿔 다시 보내면 거부한다" {
            val reviewId = openGroupReviewWithRematchPairs()
            memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = true),
            )

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(
                    author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = false),
                )
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE
            val rematch = rematchRepository.findAll().first { it.memberId1 == author && it.memberId2 == counterpart }
            rematch.wantsOf(author) shouldBe true
        }

        // 성사 응답이 유실돼 클라이언트가 재시도하면, 성사 사실을 다시 알려줄 다른 경로가 아직 없다.
        "이미 성사된 뒤 같은 내용을 다시 보내면 성사 결과를 다시 돌려준다" {
            val reviewId = openGroupReviewWithRematchPairs()
            val counterpartReviewId =
                memberReviewRepository.findByChatRoomIdAndAuthorMemberId(chatRoomId, counterpart)!!.id
            memberReviewService.submitAnswer(
                counterpart, counterpartReviewId, author, submitRequest(wantsOneToOneRematch = true),
            )
            val request = submitRequest(wantsOneToOneRematch = true)
            memberReviewService.submitAnswer(author, reviewId, counterpart, request)

            val again = memberReviewService.submitAnswer(author, reviewId, counterpart, request)

            again.rematch shouldNotBe null
            again.rematch!!.matchedMemberId shouldBe counterpart
        }

        "확정된 대상에 잘못된 값을 보내면 첫 제출과 같은 오류로 답한다" {
            val reviewId = openPersonalReview()
            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest())

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(rating = 99))
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }

        "다른 내용으로 고치려 하면 거부한다" {
            val reviewId = openPersonalReview()
            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(rating = 4))

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(rating = 5))
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE
        }
    }

    "그룹 재매칭 의사" - {
        "양쪽이 서로를 선택하면 이번 제출에서 성사 결과가 함께 온다" {
            val reviewId = openGroupReviewWithRematchPairs()
            val counterpartReviewId =
                memberReviewRepository.findByChatRoomIdAndAuthorMemberId(chatRoomId, counterpart)!!.id
            memberReviewService.submitAnswer(
                counterpart, counterpartReviewId, author, submitRequest(wantsOneToOneRematch = true),
            )

            val response = memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = true),
            )

            response.rematch shouldNotBe null
            response.rematch!!.matchedMemberId shouldBe counterpart
        }

        // 상대가 아직 고르지 않은 것과 상대가 거절한 것을 응답으로 구분할 수 없어야 한다.
        "상대가 아직 고르지 않았으면 성사 결과가 없다" {
            val reviewId = openGroupReviewWithRematchPairs()

            val response = memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = true),
            )

            response.rematch shouldBe null
        }

        "선택하지 않아도 의사는 확정된다" {
            val reviewId = openGroupReviewWithRematchPairs()

            memberReviewService.submitAnswer(
                author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = false),
            )

            val rematch = rematchRepository.findAll().first { it.memberId1 == author && it.memberId2 == counterpart }
            rematch.wantsOf(author) shouldBe false
            rematch.status shouldBe RematchStatus.WAITING
        }
    }

    "요청 검증" - {
        "그룹 평가에 재매칭 의사가 없으면 거부한다" {
            val reviewId = openGroupReviewWithRematchPairs()

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest())
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }

        "1:1 평가에 재매칭 의사를 보내면 거부한다" {
            val reviewId = openPersonalReview()

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(
                    author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = true),
                )
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }


        "별점이 범위를 벗어나면 거부한다" {
            val reviewId = openPersonalReview()

            listOf(0, 6).forEach { invalidRating ->
                val exception = shouldThrow<WarnException> {
                    memberReviewService.submitAnswer(
                        author, reviewId, counterpart, submitRequest(rating = invalidRating),
                    )
                }
                exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
            }
        }

        "코멘트가 50자를 넘으면 거부한다" {
            val reviewId = openPersonalReview()

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(
                    author, reviewId, counterpart, submitRequest(comment = "가".repeat(51)),
                )
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }
    }

    "접근 제어" - {
        // 남의 평가는 존재 여부 자체를 알려주지 않는다.
        "남의 평가에 제출하면 없는 평가로 답한다" {
            val reviewId = memberReviewRepository.let {
                memberReviewService.createReviews(endedChatRoom())
                it.findByChatRoomIdAndAuthorMemberId(100L, counterpart)!!.id
            }

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest())
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_NOT_FOUND
        }

        "없는 평가에 제출하면 거부한다" {
            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, 999L, counterpart, submitRequest())
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_NOT_FOUND
        }

        "평가 대상이 아닌 회원에게 제출하면 거부한다" {
            val reviewId = openPersonalReview()

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(author, reviewId, 99L, submitRequest())
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_TARGET
        }

        "완료된 평가의 대상에 다른 내용으로 다시 제출하면 거부한다" {
            val reviewId = openGroupReviewWithRematchPairs()
            memberReviewService.submitAnswer(author, reviewId, counterpart, submitRequest(wantsOneToOneRematch = false))
            memberReviewService.submitAnswer(
                author, reviewId, thirdMember, submitRequest(wantsOneToOneRematch = false),
            )

            val exception = shouldThrow<WarnException> {
                memberReviewService.submitAnswer(
                    author, reviewId, counterpart, submitRequest(rating = 1, wantsOneToOneRematch = false),
                )
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE
        }
    }
    // ADR 0011 이 A2 에서 확인하기로 한 지점 — 평가 행 잠금이 겹친 제출을 직렬화하는지.
    //
    // 화면은 대상을 한 명씩 제출하므로 아래처럼 서로 다른 대상이 동시에 오는 것은 정상 흐름이 아니다.
    // 다만 잠금이 지키는 계약(겹친 제출에도 진행률·완료 전이가 유실되지 않음)은 같고, 실제로 겹치는
    // 경로(더블 탭·클라이언트 재시도)보다 검증하기 쉬운 형태라 이 시나리오로 확인한다.
    // 이 테스트의 DB 는 H2 라 잠금 계약 위반은 잡지만 InnoDB 시맨틱까지 보장하지는 않는다.
    "동시 제출" - {
        "같은 평가에 제출이 겹쳐도 진행률과 완료 전이를 놓치지 않는다" {
            val reviewId = openGroupReviewWithRematchPairs()
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val submissions = listOf(counterpart, thirdMember).map { target ->
                executor.submit {
                    startLatch.await()
                    memberReviewService.submitAnswer(
                        author, reviewId, target, submitRequest(wantsOneToOneRematch = false),
                    )
                }
            }
            startLatch.countDown()
            submissions.forEach { it.get() }
            executor.shutdown()

            val review = memberReviewRepository.findById(reviewId).orElseThrow()
            review.status shouldBe ReviewProgressStatus.COMPLETED
            review.completedAt shouldNotBe null
            reviewAnswerRepository.countByMemberReviewIdAndAnsweredAtIsNull(reviewId) shouldBe 0L
        }
    }
})
