package com.ditto.api.admin.support

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 어드민 화면용 운영 주 표기 헬퍼. 주 시작일(월요일)을 받아 월요일~일요일 날짜 범위로 보여준다.
 * Thymeleaf 에서 `${@weekLabel.of(weekStartedOn)}` 로 호출.
 */
@Component("weekLabel")
class WeekLabel {
    /** 예: of(2026-07-27) -> "2026.07.27 ~ 2026.08.02" */
    fun of(weekStartedOn: LocalDate): String {
        val weekEndedOn = weekStartedOn.plusDays(6)
        return "${DATE_FORMAT.format(weekStartedOn)} ~ ${DATE_FORMAT.format(weekEndedOn)}"
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
