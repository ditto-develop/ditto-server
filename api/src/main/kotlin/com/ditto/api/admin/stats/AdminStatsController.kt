package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.api.admin.stats.dto.StatsPageData
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.system.OperationWeek
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute

/** 필터 폼만 서버가 그리고 지표는 집계 결과를 넘겨 stats.js 가 그린다(ADR 0046). */
@Controller
class AdminStatsController(
    private val serverTimeProvider: ServerTimeProvider,
    private val statsRoundFinder: StatsRoundFinder,
    private val memberStatsService: MemberStatsService,
    private val quizStatsService: QuizStatsService,
    private val memberReviewStatsService: MemberReviewStatsService,
    private val retentionStatsService: RetentionStatsService,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/admin/stats")
    fun stats(@ModelAttribute("filter") filter: StatsFilter, model: Model): String {
        val today = serverTimeProvider.now().toLocalDate()
        val currentWeek = OperationWeek.containing(today)
        val week = filter.selectedWeek(currentWeek)
        val pageData = StatsPageData(
            weekStartedOn = week.startedOn,
            today = today,
            memberStats = memberStatsService.findStats(filter, week, today),
            quizStats = quizStatsService.findStats(filter, week, today),
            memberReviewStats = memberReviewStatsService.findStats(filter, week, today),
            retentionStats = retentionStatsService.findStats(filter, week, today),
        )

        model.addAttribute("selectedWeekStartedOn", week.startedOn)
        model.addAttribute(
            "weekOptions",
            statsRoundFinder.findWeekOptionsIncluding(listOf(currentWeek.startedOn, week.startedOn)),
        )
        model.addAttribute("matchingTypeFilters", MatchingTypeFilter.entries)
        model.addAttribute("genders", Gender.entries)
        model.addAttribute("ageBands", AgeBand.entries)
        model.addAttribute("statsPageJson", objectMapper.writeValueAsString(pageData))
        model.addAttribute("active", "stats")
        return "stats/index"
    }
}
