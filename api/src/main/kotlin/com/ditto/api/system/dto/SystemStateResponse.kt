package com.ditto.api.system.dto

import com.ditto.application.system.SystemPeriod
import com.ditto.application.system.SystemState

/**
 * 현재 시스템 상태 응답. FE 홈이 period 로 화면(퀴즈/매칭/채팅)을 분기한다.
 */
data class SystemStateResponse(
    val year: Int,
    val month: Int,
    val week: Int,
    val period: SystemPeriod,
) {
    companion object {
        fun from(state: SystemState) = SystemStateResponse(
            year = state.year,
            month = state.month,
            week = state.week,
            period = state.period,
        )
    }
}
