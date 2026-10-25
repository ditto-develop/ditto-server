package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.matchesFilter
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.QMemberReview
import com.ditto.domain.review.entity.QMemberReview.memberReview
import com.ditto.domain.review.entity.QReviewAnswer
import com.ditto.domain.review.entity.QReviewAnswer.reviewAnswer
import com.ditto.domain.review.entity.ReviewAnswer
import com.querydsl.core.types.dsl.BooleanExpression
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
                member.matchesFilter(filter),
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

    override fun countMutualRatingsInWeek(weekStartedOn: LocalDate, matchType: ChatRoomType?): List<MutualRatingCount> {
        val counterpartReview = QMemberReview("counterpartReview")
        val counterpartAnswer = QReviewAnswer("counterpartAnswer")
        val countExpression = reviewAnswer.count()
        return queryFactory
            .select(memberReview.matchType, reviewAnswer.rating, counterpartAnswer.rating, countExpression)
            .from(reviewAnswer)
            .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
            .join(counterpartReview).on(
                counterpartReview.chatRoomId.eq(memberReview.chatRoomId),
                counterpartReview.authorMemberId.eq(reviewAnswer.reviewedMemberId),
            )
            .join(counterpartAnswer).on(
                counterpartAnswer.memberReviewId.eq(counterpartReview.id),
                counterpartAnswer.reviewedMemberId.eq(memberReview.authorMemberId),
                counterpartAnswer.answeredAt.isNotNull,
            )
            .where(
                memberReview.weekStartedOn.eq(weekStartedOn),
                matchType?.let { memberReview.matchType.eq(it) },
                reviewAnswer.answeredAt.isNotNull,
                memberReview.authorMemberId.lt(reviewAnswer.reviewedMemberId),
            )
            .groupBy(memberReview.matchType, reviewAnswer.rating, counterpartAnswer.rating)
            .fetch()
            .map { row ->
                MutualRatingCount(
                    matchType = row.get(memberReview.matchType)
                        ?: throw IllegalStateException("group by 키 matchType 이 비었다"),
                    rating = row.get(reviewAnswer.rating) ?: throw IllegalStateException("확정된 답인데 rating 이 비었다"),
                    counterpartRating = row.get(counterpartAnswer.rating)
                        ?: throw IllegalStateException("확정된 답인데 상대 rating 이 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun findRatingsWithMatchScoreInWeek(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
    ): List<RatingWithMatchScore> =
        queryFactory
            .select(memberReview.matchType, reviewAnswer.rating, matchCandidate.score, groupMatch.score)
            .from(reviewAnswer)
            .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
            .leftJoin(matchCandidate).on(
                memberReview.matchType.eq(ChatRoomType.PERSONAL),
                matchCandidate.quizSetId.eq(memberReview.quizSetId),
                matchCandidate.ownerMemberId.eq(memberReview.authorMemberId),
                matchCandidate.otherMemberId.eq(reviewAnswer.reviewedMemberId),
            )
            .leftJoin(groupMatch).on(
                memberReview.matchType.eq(ChatRoomType.GROUP),
                groupMatch.id.eq(memberReview.matchId),
            )
            .where(
                memberReview.weekStartedOn.eq(weekStartedOn),
                matchType?.let { memberReview.matchType.eq(it) },
                reviewAnswer.answeredAt.isNotNull,
            )
            .fetch()
            .map { row ->
                RatingWithMatchScore(
                    matchType = row.get(memberReview.matchType)
                        ?: throw IllegalStateException("matchType 이 비었다"),
                    rating = row.get(reviewAnswer.rating) ?: throw IllegalStateException("확정된 답인데 rating 이 비었다"),
                    score = row.get(matchCandidate.score) ?: row.get(groupMatch.score),
                )
            }

    override fun findAverageGivenRatingsInWeek(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<Double> = findAverageGivenRatings(weekStartedOn, matchType, filter, authorCondition = null)

    override fun findAverageGivenRatingsOfMembersCompletedAgain(
        previousWeekStartedOn: LocalDate,
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<Double> = findAverageGivenRatings(
        previousWeekStartedOn,
        matchType,
        filter,
        authorCondition = authorCompletedAnyQuizInWeek(weekStartedOn),
    )

    private fun findAverageGivenRatings(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
        authorCondition: BooleanExpression?,
    ): List<Double> =
        queryFactory
            .select(reviewAnswer.rating.avg())
            .from(reviewAnswer)
            .join(memberReview).on(memberReview.id.eq(reviewAnswer.memberReviewId))
            .join(member).on(member.id.eq(memberReview.authorMemberId))
            .where(
                memberReview.weekStartedOn.eq(weekStartedOn),
                matchType?.let { memberReview.matchType.eq(it) },
                reviewAnswer.answeredAt.isNotNull,
                member.matchesFilter(filter),
                authorCondition,
            )
            .groupBy(memberReview.authorMemberId)
            .fetch()
            .filterNotNull()

    private fun authorCompletedAnyQuizInWeek(weekStartedOn: LocalDate): BooleanExpression =
        queryFactory.selectOne()
            .from(quizProgress)
            .join(quizSet).on(quizSet.id.eq(quizProgress.quizSetId), quizSet.isActive.isTrue)
            .where(
                quizProgress.memberId.eq(memberReview.authorMemberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                quizSet.weekStartedOn.eq(weekStartedOn),
            )
            .exists()
}
