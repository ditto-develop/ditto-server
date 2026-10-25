package com.ditto.api.admin.launchnotification

import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class AdminLaunchNotificationService(
    private val launchNotificationRequestRepository: LaunchNotificationRequestRepository,
) {

    /** 화면은 건수만 보여 준다 — 이메일은 내보내기로만 꺼낸다. */
    @Transactional(readOnly = true)
    fun summary(platform: LaunchPlatform): LaunchNotificationSummary = LaunchNotificationSummary(
        platform = platform,
        pendingCount = launchNotificationRequestRepository.countByPlatformAndNotifiedAtIsNull(platform),
        notifiedCount = launchNotificationRequestRepository.countByPlatformAndNotifiedAtIsNotNull(platform),
        latestPendingId = launchNotificationRequestRepository.findFirstByPlatformAndNotifiedAtIsNullOrderByIdDesc(platform)?.id,
    )

    @Transactional(readOnly = true)
    fun countPendingRequests(): Long = launchNotificationRequestRepository.countByNotifiedAtIsNull()

    /** 아직 안내를 안 보낸 [upToId] 이하 신청의 CSV. 열은 이메일과 최초 신청 일시뿐이다. */
    @Transactional(readOnly = true)
    fun exportPendingCsv(platform: LaunchPlatform, upToId: Long): String {
        val rows = launchNotificationRequestRepository.findPendingUpTo(platform, upToId)
            .map { "${csvCell(it.email)},${it.createdAt.format(REQUESTED_AT_FORMAT)}" }
        return (listOf(CSV_HEADER) + rows).joinToString(separator = "\r\n", postfix = "\r\n")
    }

    /** @return 이번에 발송 완료로 바뀐 건수 */
    @Transactional
    fun markNotified(platform: LaunchPlatform, upToId: Long, now: LocalDateTime): Long =
        launchNotificationRequestRepository.markNotified(platform, upToId, now)

    // 이메일 형식 검사는 `=`·`+`로 시작하는 주소를 막지 않는다. 엑셀이 수식으로 실행하지 않게 앞에 '를 붙인다.
    private fun csvCell(value: String): String =
        if (value.firstOrNull() in FORMULA_PREFIXES) "'$value" else value

    companion object {
        private const val CSV_HEADER = "email,requested_at"
        private val REQUESTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
    }
}

data class LaunchNotificationSummary(
    val platform: LaunchPlatform,
    val pendingCount: Long,
    val notifiedCount: Long,
    /** 내보내기·발송 완료의 상한. 미발송이 없으면 null. */
    val latestPendingId: Long?,
)
