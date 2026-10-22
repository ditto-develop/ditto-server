package com.ditto.api.admin.stats.dto

import java.time.LocalDate

data class StatsWeekOption(
    val startedOn: LocalDate,
    val round: Int?,
)
