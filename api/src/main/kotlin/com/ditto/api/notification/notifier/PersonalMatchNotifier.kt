package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 1:1 대화 신청의 진행(신청·거절)을 **상대 한 사람에게만** 알린다 — 화면을 열고 있지 않으면 알 길이
 * 없기 때문이다(조회 응답에는 상태가 이미 실려 있어 화면이 켜져 있으면 보인다).
 *
 * 행위를 한 본인에게는 보내지 않는다. 자기가 누른 것이라 알릴 것이 없다 — [ChatVoteClosedNotifier] 가
 * 마감자를 빼는 것과 같은 기준이다.
 *
 * 매칭 건마다 한 번이다(`target_id` = `personal_match.id`). 신청은 건마다 한 번 생기고 거절은
 * `PENDING` 에서만 일어나 두 번 날 수 없지만, 중복 정책을 유형에 박아 두면 적재 지점이 늘어도
 * 판단이 갈리지 않는다.
 *
 * **실패를 삼킨다.** 적재는 [NotificationAppender] 가 흡수하지만 그 앞의 닉네임 조회는 흡수 범위
 * 밖이라 여기서 막는다 — 요청 경로에 있어 예외가 올라가면 이미 커밋된 신청·거절이 실패로 보이고,
 * 재시도해도 `MATCH_REQUEST_ALREADY_EXISTS`·`INVALID_STATUS_TRANSITION` 만 돌아온다.
 *
 * 문구에 닉네임이 들어가므로 행위자의 닉네임이 없으면(탈퇴 등) 알리지 않는다 — 주어가 빈 문구가 나간다.
 */
@Component
class PersonalMatchNotifier(
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {

    /**
     * @param matchId 생긴 매칭 건 — 중복 판정의 대상이다
     * @param receiverId 알림을 받을 사람(신청받은 사람)
     * @param requestedBy 신청한 사람 — 문구에 닉네임이 들어간다
     * @return 실제로 남겼으면 `true`. 실패했거나 이미 남긴 건이면 `false`
     */
    fun notifyRequested(matchId: Long, receiverId: Long, requestedBy: Long): Boolean =
        runCatchingExceptions { appendToReceiver(matchId, receiverId, requestedBy) }
            .onFailure { logger.warn(it) { "대화 신청 알림 실패 — 무시한다: matchId=$matchId" } }
            .getOrDefault(false)

    /**
     * @param matchId 거절된 매칭 건 — 중복 판정의 대상이다
     * @param requesterId 알림을 받을 사람(신청자)
     * @param rejectedBy 거절한 사람 — 문구에 닉네임이 들어간다
     * @return 실제로 남겼으면 `true`. 실패했거나 이미 남긴 건이면 `false`
     */
    fun notifyRejected(matchId: Long, requesterId: Long, rejectedBy: Long): Boolean =
        runCatchingExceptions { appendToRequester(matchId, requesterId, rejectedBy) }
            .onFailure { logger.warn(it) { "매칭 거절 알림 실패 — 무시한다: matchId=$matchId" } }
            .getOrDefault(false)

    private fun appendToReceiver(matchId: Long, receiverId: Long, requestedBy: Long): Boolean {
        val requesterNickname = nicknameOf(requestedBy) ?: return false
        return notificationAppender.append(
            memberId = receiverId,
            content = NotificationMessages.matchRequested(requesterNickname),
            targetId = matchId,
        )
    }

    private fun appendToRequester(matchId: Long, requesterId: Long, rejectedBy: Long): Boolean {
        val rejecterNickname = nicknameOf(rejectedBy) ?: return false
        return notificationAppender.append(
            memberId = requesterId,
            content = NotificationMessages.matchRejected(rejecterNickname),
            targetId = matchId,
        )
    }

    private fun nicknameOf(memberId: Long): String? = memberRepository.findById(memberId).orElse(null)?.nickname

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
