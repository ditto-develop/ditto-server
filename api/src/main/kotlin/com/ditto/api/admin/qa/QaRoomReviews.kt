package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaRematchPair
import com.ditto.api.admin.qa.dto.QaRematchRoom
import com.ditto.api.admin.qa.dto.QaReview
import com.ditto.api.admin.qa.dto.QaReviewTarget
import com.ditto.api.review.service.RematchSubmitter
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 방에 열린 더미 평가. 앱의 평가 조회는 작성자 본인의 미완료 평가만 주므로 저장소를 직접 읽는다. */
@Component
@Transactional(readOnly = true)
class QaRoomReviews(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val rematchRepository: RematchRepository,
    private val rematchSubmitter: RematchSubmitter,
    private val chatRoomRepository: ChatRoomRepository,
) {
    fun findDummyReviews(roomId: Long): List<QaReview> {
        val dummyIds = qaDummies.findIds()
        val reviews = memberReviewRepository.findAllByChatRoomId(roomId)
            .filter { it.authorMemberId in dummyIds }
            .sortedBy { it.authorMemberId }
        if (reviews.isEmpty()) return emptyList()

        val answersByReviewId = reviewAnswerRepository.findAllByMemberReviewIdInOrderByIdAsc(reviews.map { it.id })
            .groupBy { it.memberReviewId }
        val pairsByMatchId = rematchSubmitter.findPairsByMatchId(reviews)
        val targetIds = answersByReviewId.values.flatten().map { it.reviewedMemberId }
        val memberIds = reviews.map { it.authorMemberId } + targetIds
        val targetRows = ReviewTargetRows(
            dummyIds = dummyIds,
            members = qaMemberLabels.load(memberIds),
            rematchRoomsByRematchId = findRematchRooms(pairsByMatchId.values.flatten()),
        )

        return reviews.map { review ->
            val answers = answersByReviewId[review.id].orEmpty()
            QaReview(
                reviewId = review.id,
                author = targetRows.members.of(review.authorMemberId),
                canRematch = review.canRematch(),
                targets = targetRows.of(review, answers, pairsByMatchId[review.matchId].orEmpty()),
            )
        }
    }

    /** 의사를 낸 직후 쌍의 결과. 재매칭을 받지 않는 평가거나 아직 상대가 내지 않았으면 null 이다. */
    fun findRematchOutcome(reviewId: Long, authorId: Long, targetId: Long): QaRematchOutcome? {
        val review = memberReviewRepository.findByIdOrNull(reviewId)?.takeIf { it.canRematch() } ?: return null
        val pair = rematchRepository.findBySourceGroupMatchIdAndMemberId1AndMemberId2(
            review.matchId,
            minOf(authorId, targetId),
            maxOf(authorId, targetId),
        ) ?: return null
        return QaRematchOutcome.of(pair)
    }

    private fun findRematchRooms(pairs: List<Rematch>): Map<Long, ChatRoom> {
        val matchedIds = pairs.filter { it.status == RematchStatus.MATCHED }.map { it.id }
        if (matchedIds.isEmpty()) return emptyMap()
        return chatRoomRepository.findBySourceTypeAndSourceIdIn(ChatRoomType.REMATCH, matchedIds)
            .associateBy { it.sourceId }
    }

    private inner class ReviewTargetRows(
        val dummyIds: Set<Long>,
        val members: QaMembers,
        val rematchRoomsByRematchId: Map<Long, ChatRoom>,
    ) {
        fun of(review: MemberReview, answers: List<ReviewAnswer>, pairs: List<Rematch>): List<QaReviewTarget> {
            val authorId = review.authorMemberId
            val counterpartWantsByTargetId = rematchSubmitter.counterpartWantsByTarget(pairs, authorId)
            return answers.map { answer ->
                val targetId = answer.reviewedMemberId
                QaReviewTarget(
                    member = members.of(targetId),
                    isDummy = targetId in dummyIds,
                    isAnswered = answer.isAnswered,
                    meetingStatus = answer.meetingStatus,
                    rating = answer.rating,
                    comment = answer.comment,
                    rematch = pairs.firstOrNull { it.isBetween(authorId, targetId) }?.let { pair ->
                        pair.toQaRematchPair(authorId, counterpartWantsByTargetId[targetId])
                    },
                )
            }
        }

        private fun Rematch.toQaRematchPair(authorId: Long, counterpartWants: Boolean?) = QaRematchPair(
            rematchId = id,
            outcome = QaRematchOutcome.of(this),
            authorWants = wantsOf(authorId),
            counterpartWants = counterpartWants,
            room = rematchRoomsByRematchId[id]?.let { QaRematchRoom(it.id, it.opensAt) },
        )
    }

    private fun Rematch.isBetween(memberId: Long, otherId: Long): Boolean =
        (memberId1 == memberId && memberId2 == otherId) || (memberId1 == otherId && memberId2 == memberId)
}
