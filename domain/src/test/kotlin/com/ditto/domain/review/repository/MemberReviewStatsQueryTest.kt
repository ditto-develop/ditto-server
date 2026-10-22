package com.ditto.domain.review.repository

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.ditto.domain.review.repository.querydsl.MemberReviewCount
import com.ditto.domain.review.repository.querydsl.ReviewAnswerCount
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 10, 19)
private val ANSWERED_AT = LocalDateTime.of(2026, 10, 26, 10, 0)

class MemberReviewStatsQueryTest(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0
    var chatRoomId = 0L

    fun saveMember(gender: Gender = Gender.MALE, role: MemberRole = MemberRole.USER): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", gender = gender, role = role),
        ).id
    }

    fun saveReview(
        authorMemberId: Long,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        weekStartedOn: LocalDate = WEEK,
    ): MemberReview {
        chatRoomId += 1
        return memberReviewRepository.save(
            MemberReviewFixture.create(
                authorMemberId = authorMemberId,
                matchType = matchType,
                chatRoomId = chatRoomId,
                weekStartedOn = weekStartedOn,
            ),
        )
    }

    // 대상 하나에 답하고 평가지를 끝낸다.
    fun answer(review: MemberReview, meetingStatus: MeetingStatus, rating: Int, hasRemainingTarget: Boolean = false) {
        val answer = ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = 999L)
        answer.answer(ReviewAnswerContent.of(meetingStatus, rating, null), ANSWERED_AT)
        reviewAnswerRepository.save(answer)
        review.recordAnswer(hasRemainingTarget, ANSWERED_AT)
        memberReviewRepository.save(review)
    }

    "countMemberReviewsInWeek" - {
        "그 주 평가지를 유형·상태로 묶고 관리자·다른 주는 뺀다" {
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember(), ChatRoomType.GROUP), MeetingStatus.CHAT_ONLY, 3, hasRemainingTarget = true)
            saveReview(saveMember())
            saveReview(saveMember(role = MemberRole.ADMIN))
            saveReview(saveMember(), weekStartedOn = WEEK.minusWeeks(1))

            val counts = memberReviewRepository.countMemberReviewsInWeek(WEEK, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.COMPLETED, 1L),
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.NOT_STARTED, 1L),
                MemberReviewCount(ChatRoomType.GROUP, ReviewProgressStatus.IN_PROGRESS, 1L),
            )
        }

        "유형과 쓴 사람 조건을 건다" {
            saveReview(saveMember(Gender.FEMALE))
            saveReview(saveMember(Gender.MALE))
            saveReview(saveMember(Gender.FEMALE), ChatRoomType.GROUP)

            val counts = memberReviewRepository.countMemberReviewsInWeek(
                WEEK,
                ChatRoomType.PERSONAL,
                MemberFilter(gender = Gender.FEMALE),
            )

            counts shouldContainExactlyInAnyOrder listOf(
                MemberReviewCount(ChatRoomType.PERSONAL, ReviewProgressStatus.NOT_STARTED, 1L),
            )
        }
    }

    "countAnswersInWeek" - {
        "확정된 답만 유형·만남 상태·별점으로 묶는다" {
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember()), MeetingStatus.MET, 5)
            answer(saveReview(saveMember(), ChatRoomType.GROUP), MeetingStatus.NO_SHOW, 1)
            reviewAnswerRepository.save(ReviewAnswerFixture.pending(memberReviewId = saveReview(saveMember()).id))
            answer(saveReview(saveMember(role = MemberRole.ADMIN)), MeetingStatus.MET, 4)

            val counts = reviewAnswerRepository.countAnswersInWeek(WEEK, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                ReviewAnswerCount(ChatRoomType.PERSONAL, MeetingStatus.MET, 5, 2L),
                ReviewAnswerCount(ChatRoomType.GROUP, MeetingStatus.NO_SHOW, 1, 1L),
            )
        }
    }
})
