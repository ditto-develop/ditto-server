package com.ditto.api.match.service

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * 수락 마감까지 최소 인원([com.ditto.domain.match.entity.GroupMatch.ACTIVATION_THRESHOLD])을
 * 채우지 못한 그룹의 수락자에게 취소를 알린다.
 *
 * 알리지 않으면 수락한 사람은 왜 채팅방이 열리지 않는지 알 수 없다 — 성사 알림만 있고
 * 미성사에는 아무 신호가 없었다(QA BUG-071).
 *
 * **그룹 상태를 바꾸지 않는다.** `is_active=false` 자체가 이미 "성사되지 않음"이고,
 * 취소 플래그를 따로 두면 마이그레이션이 필요해진다. 재발송은 알림 유형의
 * `ONCE_PER_TARGET`(대상 = `group_match.id`)이 막으므로 상태 없이도 멱등이다.
 *
 * 그 멱등은 알림 행이 살아 있는 동안만이다 — 보관 기간(30일)이 지나 purge 되면 존재 검사가
 * 다시 통과한다. 그래서 스캔을 최근 [NOTIFIABLE_WINDOW_DAYS]일로 자른다. 마감 직후 안내라
 * 지난 주차를 다시 집을 이유도 없다.
 */
@Service
class UnformedGroupNotifier(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val notificationAppender: NotificationAppender,
) {

    /** @return 알림을 받은 회원 수. */
    fun notifyUnformed(now: LocalDateTime): Int {
        // 마감은 그 주 금요일 00:00. 주 시작일(월요일) 기준이라 4일을 뺀다.
        val lastWeekStartedOn = now.toLocalDate().minusDays(DAYS_FROM_MONDAY_TO_FRIDAY)
        val unformed = groupMatchRepository.findUnformedBetween(
            oldestWeekStartedOn = lastWeekStartedOn.minusDays(NOTIFIABLE_WINDOW_DAYS),
            lastWeekStartedOn = lastWeekStartedOn,
        )
        if (unformed.isEmpty()) return 0

        var notified = 0
        unformed.forEach { group ->
            val acceptedMemberIds = groupMatchMemberRepository.findByRoomId(group.id)
                .filter { it.status == InvitationStatus.ACCEPTED }
                .map { it.memberId }
            // 아무도 수락하지 않은 후보 그룹이 대부분이다 — 알릴 사람이 없으면 조용히 넘긴다.
            if (acceptedMemberIds.isEmpty()) return@forEach

            notified += notificationAppender.appendAll(
                memberIds = acceptedMemberIds,
                content = NotificationMessages.groupNotFormed(),
                targetId = group.id,
            )
        }

        if (notified > 0) {
            log.info { "그룹 인원 미달 안내 발송: 그룹 ${unformed.size}건 중 수락자 ${notified}명" }
        }
        return notified
    }

    companion object {
        private const val DAYS_FROM_MONDAY_TO_FRIDAY = 4L

        /**
         * 안내를 보낼 주차의 범위. 알림 보관 기간(30일)보다 짧아야 purge 된 뒤 다시 알리는 일이 없고,
         * 배포 중단 등으로 스케줄러가 한두 주 멈춰도 놓친 주차를 따라잡을 만큼은 길다.
         */
        private const val NOTIFIABLE_WINDOW_DAYS = 14L
    }
}
