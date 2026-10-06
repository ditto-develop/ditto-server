package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaReview
import com.ditto.api.admin.qa.dto.QaReviewTarget
import com.ditto.api.review.service.RematchSubmitter
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 방에 열린 더미 평가. 어드민은 작성자가 아니라 앱의 미완료 목록 대신 저장소를 직접 읽는다. 낸 평가도 보여 준다. */
@Component
@Transactional(readOnly = true)
class QaRoomReviews(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val rematchSubmitter: RematchSubmitter,
) {
    fun of(roomId: Long): List<QaReview> {
        val dummyIds = qaDummies.findIds()
        val reviews = memberReviewRepository.findAllByChatRoomId(roomId)
            .filter { it.authorMemberId in dummyIds }
            .sortedBy { it.authorMemberId }
        if (reviews.isEmpty()) return emptyList()

        val answersByReviewId = reviewAnswerRepository.findAllByMemberReviewIdInOrderByIdAsc(reviews.map { it.id })
            .groupBy { it.memberReviewId }
        val pairs = rematchSubmitter.findPairsByMatchId(reviews).values.flatten()
        val memberIds = reviews.map { it.authorMemberId } + answersByReviewId.values.flatten().map { it.reviewedMemberId }
        val members = qaMemberLabels.load(memberIds)

        return reviews.map { review ->
            QaReview(
                reviewId = review.id,
                author = members.of(review.authorMemberId),
                canRematch = review.canRematch(),
                targets = answersByReviewId[review.id].orEmpty().map { answer ->
                    targetOf(review, answer, members.of(answer.reviewedMemberId), pairs)
                },
            )
        }
    }

    private fun targetOf(review: MemberReview, answer: ReviewAnswer, target: QaMember, pairs: List<Rematch>): QaReviewTarget {
        val pair = pairs.firstOrNull { it.isPairOf(review.authorMemberId, target.id) }
        return QaReviewTarget(
            member = target,
            meetingStatus = answer.meetingStatus,
            rating = answer.rating,
            comment = answer.comment,
            isAnswered = answer.isAnswered,
            authorWantsRematch = pair?.wantsOf(review.authorMemberId),
            counterpartWantsRematch = pair?.wantsOf(target.id),
            rematchStatus = pair?.status,
        )
    }

    private fun Rematch.isPairOf(memberId: Long, otherId: Long): Boolean =
        setOf(memberId1, memberId2) == setOf(memberId, otherId)
}
