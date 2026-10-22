package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.nonAdminMatching
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.QMemberReview.memberReview
import com.ditto.domain.review.entity.QReviewAnswer.reviewAnswer
import com.ditto.domain.review.entity.ReviewAnswer
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Transactional(readOnly = true)
class ReviewAnswerRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ReviewAnswerRepositoryCustom {

    override fun findAllAnsweredInEndedRoomsByReviewedMemberId(reviewedMemberId: Long): List<ReviewAnswer> =
        queryFactory
            .selectFrom(reviewAnswer)
            .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
            .join(chatRoom).on(chatRoom.id.eq(memberReview.chatRoomId))
            .where(
                reviewAnswer.reviewedMemberId.eq(reviewedMemberId),
                reviewAnswer.answeredAt.isNotNull,
                chatRoom.status.eq(ChatRoomStatus.ENDED),
            )
            .orderBy(reviewAnswer.answeredAt.desc())
            .fetch()

    override fun countAnsweredInEndedRoomsByReviewedMemberIdAndMeetingStatus(
        reviewedMemberId: Long,
        meetingStatus: MeetingStatus,
    ): Long = queryFactory
        .select(reviewAnswer.count())
        .from(reviewAnswer)
        .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
        .join(chatRoom).on(chatRoom.id.eq(memberReview.chatRoomId))
        .where(
            reviewAnswer.reviewedMemberId.eq(reviewedMemberId),
            reviewAnswer.meetingStatus.eq(meetingStatus),
            reviewAnswer.answeredAt.isNotNull,
            chatRoom.status.eq(ChatRoomStatus.ENDED),
        )
        .fetchOne() ?: 0L

    override fun countAnsweredInWeek(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<ReviewAnswerCount> {
        val countExpression = reviewAnswer.count()
        return queryFactory
            .select(memberReview.matchType, reviewAnswer.meetingStatus, reviewAnswer.rating, countExpression)
            .from(reviewAnswer)
            .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
            .join(member).on(member.id.eq(memberReview.authorMemberId))
            .where(
                memberReview.weekStartedOn.eq(weekStartedOn),
                matchType?.let { memberReview.matchType.eq(it) },
                reviewAnswer.answeredAt.isNotNull,
                member.nonAdminMatching(filter),
            )
            .groupBy(memberReview.matchType, reviewAnswer.meetingStatus, reviewAnswer.rating)
            .fetch()
            .map { row ->
                ReviewAnswerCount(
                    matchType = row.get(memberReview.matchType)
                        ?: throw IllegalStateException("group by 키 matchType 이 비었다"),
                    meetingStatus = row.get(reviewAnswer.meetingStatus)
                        ?: throw IllegalStateException("확정된 답인데 meetingStatus 가 비었다"),
                    rating = row.get(reviewAnswer.rating) ?: throw IllegalStateException("확정된 답인데 rating 이 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }
}
