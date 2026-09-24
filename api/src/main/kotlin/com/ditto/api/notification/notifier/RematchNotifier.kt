package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchCancelReason
import com.ditto.domain.rematch.entity.RematchStatus
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
 */
@Component
class RematchNotifier(
    private val memberReviewRepository: MemberReviewRepository,
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

        val receiverId = receiverOf(pair, submitterId, counterpartId) ?: return false
        val actorNickname = nicknameOf(pair.counterpartOf(receiverId)) ?: return false
        val content = contentFor(pair, actorNickname)
        return notificationAppender.append(memberId = receiverId, content = content, targetId = pair.id)
    }

    /** 신청이면 상대, 거절이면 원했던 쪽. 알릴 것이 없으면 null. */
    private fun receiverOf(pair: Rematch, submitterId: Long, counterpartId: Long): Long? = when {
        pair.status == RematchStatus.WAITING && pair.wantsOf(submitterId) == true -> counterpartId
        pair.cancelReason() == RematchCancelReason.NOT_MUTUAL ->
            listOf(submitterId, counterpartId).firstOrNull { pair.wantsOf(it) == true }
        else -> null
    }

    private fun contentFor(pair: Rematch, actorNickname: String): NotificationContent =
        if (pair.status == RematchStatus.WAITING) NotificationMessages.rematchRequested(actorNickname)
        else NotificationMessages.rematchRejected(actorNickname)

    /** 닉네임이 없으면(탈퇴 등) 알리지 않는다. 주어가 빈 문구가 나간다. */
    private fun nicknameOf(memberId: Long): String? = memberRepository.findById(memberId).orElse(null)?.nickname

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
