package com.ditto.api.memberreview

import com.ditto.api.memberreview.dto.EndedChatRoom
import com.ditto.api.memberreview.service.MemberReviewService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.memberreview.entity.ReviewProgressStatus
import com.ditto.domain.memberreview.repository.MemberReviewRepository
import com.ditto.domain.memberreview.repository.ReviewAnswerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class MemberReviewServiceTest(
    private val memberReviewService: MemberReviewService,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val endedAt = LocalDateTime.of(2026, 8, 2, 23, 59, 59)

    fun endedChatRoom(
        chatRoomId: Long = 100L,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        participantIds: List<Long> = listOf(1L, 2L),
    ) = EndedChatRoom(
        chatRoomId = chatRoomId,
        matchType = matchType,
        matchId = 7L,
        quizSetId = 3L,
        weekStartedOn = LocalDate.of(2026, 7, 27),
        participantIds = participantIds,
        endedAt = endedAt,
    )

    "1:1 채팅 종료" - {
        "참여자 두 명에게 각각 진행 단위가 생기고 대상은 상대 한 명이다" {
            memberReviewService.createReviews(endedChatRoom())

            val reviews = memberReviewRepository.findAllByChatRoomId(100L)
            reviews.size shouldBe 2

            val myReview = reviews.first { it.authorMemberId == 1L }
            val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(myReview.id)
            targets.map { it.reviewedMemberId } shouldContainExactly listOf(2L)
        }

        "생성 직후에는 아직 시작하지 않은 상태이고 모든 대상이 미응답이다" {
            memberReviewService.createReviews(endedChatRoom())

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            review.status shouldBe ReviewProgressStatus.NOT_STARTED
            reviewAnswerRepository.countByMemberReviewIdAndAnsweredAtIsNull(review.id) shouldBe 1L
        }

        "종료 시점의 매칭 식별자와 주간 식별자가 그대로 기록된다" {
            memberReviewService.createReviews(endedChatRoom())

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            review.matchType shouldBe ChatRoomType.PERSONAL
            review.matchId shouldBe 7L
            review.quizSetId shouldBe 3L
            review.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
            review.availableAt shouldBe endedAt
        }
    }

    "그룹 채팅 종료" - {
        "참여자 4명이면 각자 자기 자신을 뺀 3명이 대상이 된다" {
            memberReviewService.createReviews(
                endedChatRoom(matchType = ChatRoomType.GROUP, participantIds = listOf(1L, 2L, 3L, 4L)),
            )

            val reviews = memberReviewRepository.findAllByChatRoomId(100L)
            reviews.size shouldBe 4

            reviews.forEach { review ->
                val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id)
                targets.size shouldBe 3
                targets.map { it.reviewedMemberId } shouldNotContain review.authorMemberId
            }
        }

        "대상은 참여자 명단 순서대로 생성된다" {
            memberReviewService.createReviews(
                endedChatRoom(matchType = ChatRoomType.GROUP, participantIds = listOf(1L, 2L, 3L, 4L)),
            )

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 2L)!!
            val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id)

            targets.map { it.reviewedMemberId } shouldContainExactly listOf(1L, 3L, 4L)
        }
    }

    "종료 이벤트 재처리" - {
        "같은 채팅방으로 다시 호출해도 진행 단위와 대상이 중복 생성되지 않는다" {
            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))

            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))

            memberReviewRepository.findAllByChatRoomId(100L).size shouldBe 2
            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id).size shouldBe 1
        }

        "재처리는 기존 진행 단위를 그대로 반환한다" {
            val created = memberReviewService.createReviews(endedChatRoom())

            val reprocessed = memberReviewService.createReviews(endedChatRoom())

            reprocessed.map { it.id } shouldContainExactly created.map { it.id }
        }
    }

    "참여자 명단 검증" - {
        "참여자가 한 명뿐이면 평가를 열 수 없다" {
            val exception = shouldThrow<WarnException> {
                memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L)))
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_TARGET
        }

        "중복된 참여자는 한 번만 센다" {
            val exception = shouldThrow<WarnException> {
                memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 1L)))
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_TARGET
        }
    }
})
