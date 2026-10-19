package com.ditto.api.userreport.service

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportSource
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 신고 접수 수를 Grafana 알림용으로 센다.
 * 태그는 심각 사유 여부·접수 위치만 둔다 — 회원 식별값은 메트릭에 싣지 않는다.
 */
@Component
class UserReportMetrics(
    private val meterRegistry: MeterRegistry,
) {

    init {
        // 조합마다 0으로 미리 만든다. 처음 생긴 시계열의 첫 1은 increase()가 못 잡아서 첫 신고 알림이 빠진다.
        listOf(true, false).forEach { severe ->
            MemberReportSource.entries.forEach { source -> counter(severe, source.code) }
        }
    }

    /** 커밋된 신고만 센다 — 롤백된 신고로 알림이 울리면 안 된다. */
    fun countReceivedAfterCommit(report: MemberReport) {
        val severe = report.reasons.any { it.isSevere }
        val source = report.source.code
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            countReceived(severe, source)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = countReceived(severe, source)
            },
        )
    }

    private fun countReceived(severe: Boolean, source: String) = counter(severe, source).increment()

    private fun counter(severe: Boolean, source: String): Counter =
        Counter.builder(RECEIVED_METRIC)
            .description("접수된 회원 신고 수")
            .tag("severe", severe.toString())
            .tag("source", source)
            .register(meterRegistry)

    companion object {
        const val RECEIVED_METRIC = "ditto.user.reports.received"
    }
}
