package com.ditto.api.user

import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.service.MemberRatingService
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MemberRatingServiceTest(
    private val memberRatingService: MemberRatingService,
    private val memberRepository: MemberRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberSequence = 0
    var groupMatchSequence = 1L

    fun saveMemberId(): Long =
        memberRepository.save(Member(nickname = "회원${memberSequence++}").apply { activate() }).id

    fun saveRoomId(ended: Boolean = true): Long {
        val sourceId = groupMatchSequence++
        val room = if (ended) ChatRoomFixture.endedGroup(sourceId) else ChatRoomFixture.group(sourceId)
        return chatRoomRepository.save(room).id
    }

    fun receiveRatings(reviewedMemberId: Long, vararg ratings: Int, chatRoomId: Long = saveRoomId()) {
        ratings.forEach { rating ->
            val review = memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = saveMemberId(), chatRoomId = chatRoomId),
            )
            val answer = reviewAnswerRepository.save(
                ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = reviewedMemberId),
            )
            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, rating, null), LocalDateTime.now())
            reviewAnswerRepository.save(answer)
        }
    }

    "평균 별점 반올림" - {
        "3.666…은 3.7로 올린다" {
            val memberId = saveMemberId()
            receiveRatings(memberId, 4, 4, 3)

            memberRatingService.getRatings(memberId).averageScore shouldBe 3.7
        }

        "3.333…은 3.3으로 내린다" {
            val memberId = saveMemberId()
            receiveRatings(memberId, 4, 3, 3)

            memberRatingService.getRatings(memberId).averageScore shouldBe 3.3
        }

        "3.25는 3.3으로 올린다" {
            val memberId = saveMemberId()
            receiveRatings(memberId, 3, 3, 3, 4)

            memberRatingService.getRatings(memberId).averageScore shouldBe 3.3
        }

        "프로필 평균도 반올림된 값이다" {
            val memberId = saveMemberId()
            receiveRatings(memberId, 4, 4, 3)

            memberRatingService.findPublicAverageScore(memberId) shouldBe 3.7
        }
    }

    "아직 열린 방에서 나간 사람이 먼저 쓴 평가는 방이 끝날 때까지 세지 않는다" {
        val memberId = saveMemberId()
        receiveRatings(memberId, 4)
        receiveRatings(memberId, 1, chatRoomId = saveRoomId(ended = false))

        memberRatingService.getRatings(memberId).totalCount shouldBe 1
    }
})
