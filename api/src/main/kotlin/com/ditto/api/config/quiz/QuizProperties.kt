package com.ditto.api.config.quiz

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.DayOfWeek

@ConfigurationProperties(prefix = "ditto.quiz")
data class QuizProperties(
    val availableDays: Set<DayOfWeek> = emptySet(),
)
