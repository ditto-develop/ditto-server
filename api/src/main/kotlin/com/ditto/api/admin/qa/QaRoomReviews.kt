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
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
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
        val sources = ReviewSources(
            dummyIds = dummyIds,
            members = qaMemberLabels.load(reviews.map { it.authorMemberId } + answersByReviewId.values.flatten().map { it.reviewedMemberId }),
            rematchRoomsByRematchId = findRematchRooms(pairsByMatchId.values.flatten()),
        )

        return reviews.map { review ->
            val pairs = pairsByMatchId[review.matchId].orEmpty()
            QaReview(
                reviewId = review.id,
                author = sources.members.of(review.authorMemberId),
                canRematch = review.canRematch(),
                targets = answersByReviewId[review.id].orEmpty().map { sources.targetOf(review, it, pairs) },
            )
        }
    }

    private fun findRematchRooms(pairs: List<Rematch>): Map<Long, ChatRoom> {
        val matchedIds = pairs.filter { it.matchedAt() != null }.map { it.id }
        if (matchedIds.isEmpty()) return emptyMap()
        return chatRoomRepository.findBySourceTypeAndSourceIdIn(ChatRoomType.REMATCH, matchedIds).associateBy { it.sourceId }
    }

    private inner class ReviewSources(
        val dummyIds: Set<Long>,
        val members: QaMembers,
        val rematchRoomsByRematchId: Map<Long, ChatRoom>,
    ) {
        fun targetOf(review: MemberReview, answer: ReviewAnswer, pairs: List<Rematch>): QaReviewTarget {
            val targetId = answer.reviewedMemberId
            return QaReviewTarget(
                member = members.of(targetId),
                isDummy = targetId in dummyIds,
                isAnswered = answer.isAnswered,
                meetingStatus = answer.meetingStatus,
                rating = answer.rating,
                comment = answer.comment,
                rematch = pairs.firstOrNull { it.involves(review.authorMemberId, targetId) }
                    ?.let { pairOf(it, review.authorMemberId, pairs) },
            )
        }

        private fun pairOf(pair: Rematch, authorId: Long, pairs: List<Rematch>): QaRematchPair {
            val counterpartId = pair.counterpartOf(authorId)
            return QaRematchPair(
                rematchId = pair.id,
                status = pair.status,
                isCancelledByMemberLeave = pair.isCancelledByMemberLeave(),
                authorWants = pair.wantsOf(authorId),
                counterpartWants = rematchSubmitter.counterpartWantsByTarget(pairs, authorId)[counterpartId],
                room = rematchRoomsByRematchId[pair.id]?.let { QaRematchRoom(it.id, it.opensAt) },
            )
        }
    }

    private fun Rematch.involves(memberId: Long, otherId: Long): Boolean =
        (memberId1 == memberId && memberId2 == otherId) || (memberId1 == otherId && memberId2 == memberId)
}
