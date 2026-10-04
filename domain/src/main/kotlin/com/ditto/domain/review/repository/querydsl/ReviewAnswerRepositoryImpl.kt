package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.QMemberReview.memberReview
import com.ditto.domain.review.entity.QReviewAnswer.reviewAnswer
import com.ditto.domain.review.entity.ReviewAnswer
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

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
}
