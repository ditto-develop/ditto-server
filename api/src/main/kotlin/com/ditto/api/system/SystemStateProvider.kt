package com.ditto.api.system

import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/** 서버 현재 시각 기준의 (주간/기간) 상태. year/month/week는 운영 주 시작일 기준 표시값. */
data class SystemState(
    val year: Int,
    val month: Int,
    val week: Int,
    val weekStartedOn: LocalDate,
    val period: SystemPeriod,
    val dateTime: LocalDateTime,
)

/**
 * 서버 현재 시각([ServerTimeProvider])으로부터 시스템 상태를 계산한다.
 * 주간 식별자는 현재 시각이 속한 [OperationWeek](그 주 월요일)를 기준으로 한다.
 */
@Component
class SystemStateProvider(
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun current(): SystemState {
        val now = serverTimeProvider.now()
        val currentWeek = OperationWeek.containing(now.toLocalDate())
        return SystemState(
            year = currentWeek.year,
            month = currentWeek.month,
            week = currentWeek.weekOfMonth,
            weekStartedOn = currentWeek.startedOn,
            period = SystemPeriod.from(now.dayOfWeek),
            dateTime = now,
        )
    }
}
