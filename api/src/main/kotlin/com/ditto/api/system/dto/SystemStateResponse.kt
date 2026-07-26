package com.ditto.api.system.dto

import com.ditto.api.system.SystemPeriod
import com.ditto.api.system.SystemState
import java.time.LocalDate

/**
 * 현재 시스템 상태 응답. FE 홈이 period 로 화면(퀴즈/매칭/채팅)을 분기한다.
 * 주간 식별자는 weekStartedOn(운영 주 시작 월요일)이며 year/month/week는 그 파생 표시값.
 */
data class SystemStateResponse(
    val weekStartedOn: LocalDate,
    val year: Int,
    val month: Int,
    val week: Int,
    val period: SystemPeriod,
) {
    companion object {
        fun from(state: SystemState) = SystemStateResponse(
            weekStartedOn = state.weekStartedOn,
            year = state.year,
            month = state.month,
            week = state.week,
            period = state.period,
        )
    }
}
