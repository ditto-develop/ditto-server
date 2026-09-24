package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 1:1 대화 신청·수락·거절을 상대 한 사람에게 알린다. 화면을 열고 있지 않으면 알 길이 없어서다.
 *
 * 행위를 한 본인에게는 보내지 않는다. 행위자 닉네임이 없으면(탈퇴 등) 주어가 빈 문구가 되므로
 * 알리지 않는다.
 *
 * 매칭 건마다 한 번이다(target_id = personal_match.id). 신청·수락·거절 모두 한 건에 한 번만
 * 일어나지만 중복 정책은 유형에 둔다.
 *
 * 닉네임 조회 실패는 여기서 삼킨다. 요청 경로라 예외가 올라가면 이미 커밋된 처리가 실패로 보인다.
 */
@Component
class PersonalMatchNotifier(
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {

    /** 수신자에게 신청 사실을 알린다. 실제로 남겼으면 true. */
    fun notifyRequested(matchId: Long, receiverId: Long, requestedBy: Long): Boolean =
        runCatchingExceptions {
            appendToCounterpart(matchId, receiverId, nicknameOf(requestedBy)?.let(NotificationMessages::matchRequested))
        }
            .onFailure { logger.warn(it) { "대화 신청 알림 실패 — 무시한다: matchId=$matchId" } }
            .getOrDefault(false)

    /** 신청자에게 수락 사실을 알린다. 실제로 남겼으면 true. */
    fun notifyAccepted(matchId: Long, requesterId: Long, acceptedBy: Long): Boolean =
        runCatchingExceptions {
            appendToCounterpart(matchId, requesterId, nicknameOf(acceptedBy)?.let(NotificationMessages::matchAccepted))
        }
            .onFailure { logger.warn(it) { "대화 신청 수락 알림 실패 — 무시한다: matchId=$matchId" } }
            .getOrDefault(false)

    /** 신청자에게 거절 사실을 알린다. 실제로 남겼으면 true. */
    fun notifyRejected(matchId: Long, requesterId: Long, rejectedBy: Long): Boolean =
        runCatchingExceptions {
            appendToCounterpart(matchId, requesterId, nicknameOf(rejectedBy)?.let(NotificationMessages::matchRejected))
        }
            .onFailure { logger.warn(it) { "대화 신청 거절 알림 실패 — 무시한다: matchId=$matchId" } }
            .getOrDefault(false)

    /** content 가 null 이면(행위자 닉네임 없음) 알리지 않는다. */
    private fun appendToCounterpart(matchId: Long, counterpartId: Long, content: NotificationContent?): Boolean {
        if (content == null) {
            return false
        }
        return notificationAppender.append(memberId = counterpartId, content = content, targetId = matchId)
    }

    private fun nicknameOf(memberId: Long): String? = memberRepository.findById(memberId).orElse(null)?.nickname

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
