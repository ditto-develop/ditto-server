package com.ditto.api.admin.cleanup

import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component

/** 평가와 평가 답변을 지운다. 방 기준은 매칭 기록 정리가, 회원 기준은 더미 정리가 쓴다. */
@Component
class ReviewEraser(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {
    fun eraseInRooms(roomIds: Collection<Long>) {
        if (roomIds.isEmpty()) return

        val reviewIds = memberReviewRepository.findByChatRoomIdIn(roomIds).map { it.id }
        eraseWithAnswers(reviewIds, extraAnswerIds = emptyList())
    }

    /** 회원이 쓴 평가와, 다른 사람이 그 회원을 대상으로 남긴 답변. */
    fun eraseByMembers(memberIds: Collection<Long>) {
        if (memberIds.isEmpty()) return

        val reviewIds = memberReviewRepository.findByAuthorMemberIdIn(memberIds).map { it.id }
        val answerIdsAboutMembers = reviewAnswerRepository.findByReviewedMemberIdIn(memberIds).map { it.id }
        eraseWithAnswers(reviewIds, answerIdsAboutMembers)
    }

    private fun eraseWithAnswers(reviewIds: List<Long>, extraAnswerIds: List<Long>) {
        val answerIdsOfReviews =
            if (reviewIds.isEmpty()) emptyList()
            else reviewAnswerRepository.findAllByMemberReviewIdInOrderByIdAsc(reviewIds).map { it.id }
        reviewAnswerRepository.deleteAllByIdInBatch((answerIdsOfReviews + extraAnswerIds).toSet())
        memberReviewRepository.deleteAllByIdInBatch(reviewIds)
    }
}
