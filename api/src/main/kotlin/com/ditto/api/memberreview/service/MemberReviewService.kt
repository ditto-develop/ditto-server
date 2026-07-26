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
     * 같은 채팅방으로 다시 호출해도 기존 결과를 그대로 반환한다 — 종료 이벤트가 재전달되거나
     * 누락 복구가 돌아도 평가가 중복 생성되지 않아야 하기 때문이다.
     */
    fun createReviews(endedChatRoom: EndedChatRoom): List<MemberReview> {
        val participantIds = endedChatRoom.participantIds.distinct()
        if (participantIds.size < MIN_PARTICIPANT_COUNT) {
            throw WarnException(ErrorCode.INVALID_REVIEW_TARGET, "평가 대상이 없어 평가를 열 수 없습니다.")
        }
        return participantIds.map { authorMemberId ->
            createReviewFor(authorMemberId, participantIds, endedChatRoom)
        }
    }

    private fun createReviewFor(
        authorMemberId: Long,
        participantIds: List<Long>,
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
        val pendingAnswers =
            participantIds
                .filter { it != authorMemberId }
                .map { ReviewAnswer.pending(memberReviewId = review.id, reviewedMemberId = it) }

        reviewAnswerRepository.saveAll(pendingAnswers)
        return review
    }

    companion object {
        /** 평가는 최소 두 명이 있어야 성립한다(자기 자신은 대상에서 빠지므로). */
        private const val MIN_PARTICIPANT_COUNT = 2
    }
}
