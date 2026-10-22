package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

private val FIRST = OperationWeek(LocalDate.of(2026, 9, 28))

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

    // 첫 주, 둘째 주(비활성만), 셋째 주, 넷째 주가 회차 후보다.
    fun saveRounds() {
        saveQuizSet(FIRST)
        saveQuizSet(OperationWeek(FIRST.startedOn.plusWeeks(1)), isActive = false)
        saveQuizSet(OperationWeek(FIRST.startedOn.plusWeeks(2)))
        saveQuizSet(OperationWeek(FIRST.startedOn.plusWeeks(3)))
    }

    "findWeekOptionsIncluding" - {
        "비활성 셋만 있는 주는 회차로 세지 않아 번호가 없다" {
            saveRounds()
            val inactiveWeek = FIRST.startedOn.plusWeeks(1)

            statsRoundFinder.findWeekOptionsIncluding(listOf(inactiveWeek)) shouldBe listOf(
                StatsWeekOption(FIRST.startedOn.plusWeeks(3), 3),
                StatsWeekOption(FIRST.startedOn.plusWeeks(2), 2),
                StatsWeekOption(inactiveWeek, null),
                StatsWeekOption(FIRST.startedOn, 1),
            )
        }
    }

    "findRecentRoundWeeksUntil" - {
        "고른 주를 포함해 앞쪽 회차를 최신순으로 정한 개수만큼 준다" {
            saveRounds()

            statsRoundFinder.findRecentRoundWeeksUntil(OperationWeek(FIRST.startedOn.plusWeeks(2)), 4) shouldBe
                listOf(FIRST.startedOn.plusWeeks(2), FIRST.startedOn)
        }
    }

    "findPreviousRoundWeek" - {
        "회차가 아닌 주를 건너뛴 바로 앞 회차다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(OperationWeek(FIRST.startedOn.plusWeeks(2))) shouldBe FIRST.startedOn
        }

        "앞에 회차가 없으면 null 이다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(FIRST) shouldBe null
        }

        "고른 주가 회차가 아니면 앞에 회차가 있어도 null 이다" {
            saveRounds()

            statsRoundFinder.findPreviousRoundWeek(OperationWeek(FIRST.startedOn.plusWeeks(1))) shouldBe null
        }
    }
})
