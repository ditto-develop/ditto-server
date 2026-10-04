package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchCancelReason
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 재매칭 의사 제출이 상대에게 뜻하는 것을 알린다 — 신청(내가 먼저 원함) 또는 거절(한쪽이 원했는데 성사 안 됨).
 * 성사 알림은 방이 예약될 때 RematchChatRoomOpener 가 보낸다.
 *
 * 제출 커밋 뒤 컨트롤러가 부른다. 제출 서비스는 응답 DTO 만 돌려주므로 여기서 쌍을 잠금 없이 다시 읽는다.
 * 제출은 한 사람당 한 번이라 제출 직후의 쌍 상태가 곧 이번 제출의 결과다.
 * 쌍마다 한 번이다(target_id = rematch.id). 재제출이 들어와도 중복 정책이 막는다.
 *
 * 조회 실패는 여기서 삼킨다. 요청 경로라 예외가 올라가면 이미 커밋된 제출이 실패로 보인다.
 *
 * 신청을 받을 사람에게 아직 평가지가 없으면(열린 방에서 나간 사람이 먼저 원했을 때) 보내지 않는다.
 * 눌러도 수락할 화면이 없기 때문이다. 그 신청은 방이 끝나 평가가 열릴 때 [notifyWaitingRequestsFor]가 보낸다.
 */
@Component
class RematchNotifier(
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val rematchRepository: RematchRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 실제로 남겼으면 true. 알릴 것이 없거나 실패했으면 false. */
    fun notifySubmitted(reviewId: Long, submitterId: Long, counterpartId: Long): Boolean =
        runCatchingExceptions { appendForSubmission(reviewId, submitterId, counterpartId) }
            .onFailure { logger.warn(it) { "재매칭 제출 알림 실패 — 무시한다: reviewId=$reviewId" } }
            .getOrDefault(false)

    private fun appendForSubmission(reviewId: Long, submitterId: Long, counterpartId: Long): Boolean {
        val review = memberReviewRepository.findById(reviewId).orElse(null) ?: return false
        if (!review.canRematch()) {
            return false
        }
        val pair = rematchRepository.findBySourceGroupMatchIdAndMemberId1AndMemberId2(
            sourceGroupMatchId = review.matchId,
            memberId1 = minOf(submitterId, counterpartId),
            memberId2 = maxOf(submitterId, counterpartId),
        ) ?: return false

        if (pair.requesterId() == submitterId) {
            return appendRequest(pair)
        }
        return appendRejection(pair, submitterId, counterpartId)
    }

    /**
     * 방이 끝나 평가가 열린 그룹 방들에서, 한쪽만 원하고 기다리는 쌍의 상대에게 신청을 알린다.
     * 평가지가 없어 미뤄 둔 신청을 보내는 자리다. 쌍마다 한 번이라 이미 받은 사람에게는 다시 가지 않는다.
     */
    fun notifyWaitingRequestsFor(endedRoomIds: Collection<Long>): Int =
        runCatchingExceptions { appendWaitingRequests(endedRoomIds) }
            .onFailure { logger.warn(it) { "재매칭 대기 신청 알림 실패, 무시한다: roomIds=$endedRoomIds" } }
            .getOrDefault(0)

    private fun appendWaitingRequests(endedRoomIds: Collection<Long>): Int {
        if (endedRoomIds.isEmpty()) {
            return 0
        }
        val groupMatchIds = chatRoomRepository.findAllById(endedRoomIds)
            .filter { it.sourceType == ChatRoomType.GROUP }
            .map { it.sourceId }
        if (groupMatchIds.isEmpty()) {
            return 0
        }
        return rematchRepository.findAllBySourceGroupMatchIdIn(groupMatchIds)
            .count { pair -> appendRequest(pair) }
    }

    /** 신청자의 상대에게 알린다. 상대에게 평가지가 없으면 수락할 화면이 없어 보내지 않는다. */
    private fun appendRequest(pair: Rematch): Boolean {
        val requesterId = pair.requesterId() ?: return false
        val receiverId = pair.counterpartOf(requesterId)
        if (!hasReviewForm(pair.sourceChatRoomId, receiverId)) {
            return false
        }
        val requesterNickname = nicknameOf(requesterId) ?: return false
        return notificationAppender.append(
            memberId = receiverId,
            content = NotificationMessages.rematchRequested(requesterNickname),
            targetId = pair.id,
        )
    }

    /** 한쪽이 원했는데 상호가 아니라 취소됐으면 원했던 쪽에 알린다. */
    private fun appendRejection(pair: Rematch, submitterId: Long, counterpartId: Long): Boolean {
        if (pair.cancelReason() != RematchCancelReason.NOT_MUTUAL) {
            return false
        }
        val receiverId = listOf(submitterId, counterpartId).firstOrNull { pair.wantsOf(it) == true } ?: return false
        val rejecterNickname = nicknameOf(pair.counterpartOf(receiverId)) ?: return false
        return notificationAppender.append(
            memberId = receiverId,
            content = NotificationMessages.rematchRejected(rejecterNickname),
            targetId = pair.id,
        )
    }

    private fun hasReviewForm(chatRoomId: Long, memberId: Long): Boolean =
        memberReviewRepository.findByChatRoomIdAndAuthorMemberId(chatRoomId, memberId) != null

    /** 닉네임이 없으면(탈퇴 등) 알리지 않는다. 주어가 빈 문구가 나간다. */
    private fun nicknameOf(memberId: Long): String? = memberRepository.findById(memberId).orElse(null)?.nickname

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
