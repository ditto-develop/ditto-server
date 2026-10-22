package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

private val FIRST_ROUND_WEEK = OperationWeek(LocalDate.of(2026, 9, 28))
private val INACTIVE_ONLY_WEEK = OperationWeek(LocalDate.of(2026, 10, 5))
private val SECOND_ROUND_WEEK = OperationWeek(LocalDate.of(2026, 10, 12))
private val THIRD_ROUND_WEEK = OperationWeek(LocalDate.of(2026, 10, 19))

class StatsRoundFinderTest(
    private val statsRoundFinder: StatsRoundFinder,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveQuizSet(week: OperationWeek, isActive: Boolean = true) = quizSetRepository.save(
        QuizSetFixture.create(
            startDate = week.dateTimeRange.start,
            endDate = week.startedOn.plusDays(2).atTime(23, 59, 59),
            isActive = isActive,
        ),
    )

    fun saveRounds() {
        saveQuizSet(FIRST_ROUND_WEEK)
        saveQuizSet(INACTIVE_ONLY_WEEK, isActive = false)
        saveQuizSet(SECOND_ROUND_WEEK)
        saveQuizSet(THIRD_ROUND_WEEK)
    }

    "findWeekOptionsIncluding" - {
        "비활성 셋만 있는 주는 회차로 세지 않아 번호가 없다" {
            saveRounds()

            statsRoundFinder.findWeekOptionsIncluding(listOf(INACTIVE_ONLY_WEEK.startedOn)) shouldBe listOf(
                StatsWeekOption(THIRD_ROUND_WEEK.startedOn, 3),
                StatsWeekOption(SECOND_ROUND_WEEK.startedOn, 2),
                StatsWeekOption(INACTIVE_ONLY_WEEK.startedOn, null),
                StatsWeekOption(FIRST_ROUND_WEEK.startedOn, 1),
            )
        }
    }

    "findRecentRoundWeeksUntil" - {
        "고른 주와 그 앞의 회차만 최신순으로 준다" {
            saveRounds()

            statsRoundFinder.findRecentRoundWeeksUntil(SECOND_ROUND_WEEK, 4) shouldBe
                listOf(SECOND_ROUND_WEEK.startedOn, FIRST_ROUND_WEEK.startedOn)
        }

        "정한 개수를 넘으면 최근 회차부터 자른다" {
            saveRounds()

            statsRoundFinder.findRecentRoundWeeksUntil(THIRD_ROUND_WEEK, 2) shouldBe
                listOf(THIRD_ROUND_WEEK.startedOn, SECOND_ROUND_WEEK.startedOn)
        }
    }

    "findPreviousRoundWeek" - {
        "회차가 아닌 주를 건너뛴 바로 앞 회차다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(SECOND_ROUND_WEEK) shouldBe FIRST_ROUND_WEEK.startedOn
        }

        "앞에 회차가 없으면 null 이다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(FIRST_ROUND_WEEK) shouldBe null
        }

        "고른 주가 회차가 아니면 앞에 회차가 있어도 null 이다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(INACTIVE_ONLY_WEEK) shouldBe null
        }
    }
})
