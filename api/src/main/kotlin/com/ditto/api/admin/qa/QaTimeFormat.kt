package com.ditto.api.admin.qa

import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** QA 콘솔이 시각을 보여주는 형식. 템플릿은 `${@qaTime.of(dateTime)}`로, 코드는 [format]으로 같은 형식을 쓴다. */
@Component("qaTime")
class QaTimeFormat {
    fun of(dateTime: LocalDateTime): String = format(dateTime)

    companion object {
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN)

        fun format(dateTime: LocalDateTime): String = FORMATTER.format(dateTime)
    }
}
