package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.entity.Sanction
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 신고 검토가 제재로 끝났음을 신고자와 피신고자에게 알린다.
 *
 * - 신고자(`REPORT_ACTIONED`, target = 신고 건): 처리됐다는 사실만. 제재 수위·피신고자는 밝히지 않는다.
 * - 피신고자(`SANCTION_IMPOSED`, target = 제재): 수위와 기간.
 *
 * 검토 커밋 뒤 어드민 컨트롤러가 부른다. 둘 다 SYSTEM 카테고리라 수신 설정과 무관하게 푸시가 나간다.
 * 제재는 refresh 토큰을 회수할 뿐 기기 토큰은 남기므로 정지·차단된 회원에게도 푸시가 닿는다.
 *
 * 조회 실패는 여기서 삼킨다. 요청 경로라 예외가 올라가면 이미 커밋된 검토가 실패로 보인다.
 */
@Component
class ReportSanctionNotifier(
    private val memberReportRepository: MemberReportRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** @return 실제로 남긴 알림 수. 실패했으면 0 */
    fun notifyImposed(sanction: Sanction): Int =
        runCatchingExceptions { appendFor(sanction) }
            .onFailure { logger.warn(it) { "신고 제재 알림 실패 — 무시한다: sanctionId=${sanction.id}" } }
            .getOrDefault(0)

    private fun appendFor(sanction: Sanction): Int {
        val sanctioned = notificationAppender.append(
            memberId = sanction.memberId,
            content = NotificationMessages.sanctionImposed(sanction.level, sanction.startsAt, sanction.endsAt),
            targetId = sanction.id,
        )
        val report = sanction.memberReportId?.let { memberReportRepository.findById(it).orElse(null) }
        val reporter = report?.let {
            notificationAppender.append(
                memberId = it.reporterId,
                content = NotificationMessages.reportActioned(),
                targetId = it.id,
            )
        } ?: false
        return listOf(sanctioned, reporter).count { it }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
