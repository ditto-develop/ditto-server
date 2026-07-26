package com.ditto.api.memberreview.service

import com.ditto.api.memberreview.dto.EndedChatRoom
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreview.entity.MemberReview
import com.ditto.domain.memberreview.entity.ReviewAnswer
import com.ditto.domain.memberreview.repository.MemberReviewRepository
import com.ditto.domain.memberreview.repository.ReviewAnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MemberReviewService(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {
    /**
     * 종료된 채팅방의 참여자별 평가를 연다. 참여자마다 진행 단위 1건과 자기 자신을 제외한 대상 행을 만든다.
     *
     * **같은 스냅샷으로** 다시 호출하면 기존 진행 단위를 그대로 반환한다. 다만 이미 만들어진 진행 단위의
     * 대상 행은 다시 맞추지 않으므로, 참여자 명단이 달라진 채로 재전달되면 새 대상이 반영되지 않는다 —
     * 종료 이벤트는 동일한 참여자 스냅샷으로 재전달돼야 한다.
     *
     * 같은 채팅방으로 **동시에** 들어오면 유일키가 중복 생성을 막고 진 쪽 호출이 실패한다.
     * 재전달·재시도·누락 복구는 채팅 종료 어댑터가 책임진다.
     *
     * 평가할 상대가 없으면(참여자 1명) 아무것도 만들지 않고 빈 목록을 반환한다 —
     * 시스템이 부르는 흐름이라 여기서 예외를 던지면 채팅 종료 자체가 실패한다.
     */
    fun createReviews(endedChatRoom: EndedChatRoom): List<MemberReview> {
        val reviewerIds = endedChatRoom.reviewerIds
        if (reviewerIds.isEmpty()) {
            throw WarnException(ErrorCode.INVALID_REVIEW_TARGET, "참여자가 없는 채팅방입니다.")
        }
        if (reviewerIds.size < MIN_REVIEWER_COUNT) {
            return emptyList()
        }
        return reviewerIds.map { createReviewFor(it, endedChatRoom) }
    }

    private fun createReviewFor(
        authorMemberId: Long,
        endedChatRoom: EndedChatRoom,
    ): MemberReview {
        val existing =
            memberReviewRepository.findByChatRoomIdAndAuthorMemberId(endedChatRoom.chatRoomId, authorMemberId)
        if (existing != null) {
            return existing
        }

        val review = memberReviewRepository.save(
            MemberReview.create(
                authorMemberId = authorMemberId,
                matchType = endedChatRoom.matchType,
                matchId = endedChatRoom.matchId,
                chatRoomId = endedChatRoom.chatRoomId,
                quizSetId = endedChatRoom.quizSetId,
                weekStartedOn = endedChatRoom.weekStartedOn,
                availableAt = endedChatRoom.endedAt,
            ),
        )
        val pendingAnswers = endedChatRoom
            .targetIdsFor(authorMemberId)
            .map { ReviewAnswer.pending(memberReviewId = review.id, reviewedMemberId = it) }

        reviewAnswerRepository.saveAll(pendingAnswers)
        return review
    }

    companion object {
        /** 평가는 최소 두 명이 있어야 성립한다(자기 자신은 대상에서 빠지므로). */
        private const val MIN_REVIEWER_COUNT = 2
    }
}
