package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSetStat
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizSetStatQueryTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 11, 5, 0, 0)

    fun endedSet(endDate: LocalDateTime) = quizSetRepository.save(
        QuizSetFixture.create(startDate = endDate.minusDays(2).toLocalDate().atStartOfDay(), endDate = endDate),
    )

    "findEndedQuizSetIdsWithoutStat — 스냅샷 배치 대상" - {
        "창 안에서 마감됐고 스냅샷이 없는 셋만 나온다" {
            val target = endedSet(LocalDateTime.of(2026, 11, 4, 23, 59, 59))
            val alreadySnapshotted = endedSet(LocalDateTime.of(2026, 11, 4, 23, 59, 59))
            quizSetStatRepository.save(QuizSetStat.of(alreadySnapshotted.id, participantCount = 0, overlapThreshold = 8))
            endedSet(LocalDateTime.of(2026, 10, 1, 23, 59, 59)) // 창 밖
            endedSet(LocalDateTime.of(2026, 11, 11, 23, 59, 59)) // 마감 전

            quizSetRepository.findEndedQuizSetIdsWithoutStat(now.minusDays(14), now) shouldBe listOf(target.id)
        }
    }

    "findCompletedQuizSetsOf — 퀴즈 기록 목록" - {
        "완주한 셋만 최신 운영 주부터 준다(활성 여부는 보지 않는다)" {
            val older = quizSetRepository.save(
                QuizSetFixture.create(startDate = LocalDateTime.of(2026, 10, 26, 0, 0), endDate = LocalDateTime.of(2026, 10, 28, 23, 59, 59), isActive = false),
            )
            val newer = quizSetRepository.save(
                QuizSetFixture.create(startDate = LocalDateTime.of(2026, 11, 2, 0, 0), endDate = LocalDateTime.of(2026, 11, 4, 23, 59, 59)),
            )
            val stopped = quizSetRepository.save(
                QuizSetFixture.create(startDate = LocalDateTime.of(2026, 10, 19, 0, 0), endDate = LocalDateTime.of(2026, 10, 21, 23, 59, 59)),
            )
            listOf(older, newer).forEach { set ->
                quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = set.id, totalCount = 1).apply { recordAnswer() })
            }
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = stopped.id, totalCount = 2).apply { recordAnswer() })

            val result = quizSetRepository.findCompletedQuizSetsOf(1L)

            result.map { it.id } shouldBe listOf(newer.id, older.id)
            result.first().weekStartedOn shouldBe LocalDate.of(2026, 11, 2)
        }

        "완주한 셋이 없으면 빈 목록이다" {
            quizSetRepository.findCompletedQuizSetsOf(1L).shouldBeEmpty()
        }
    }

    "countCompletedWeeksOf — 참여 주 수" - {
        "같은 주에 두 셋을 완주해도 한 주로 세고 끝까지 풀지 않은 셋은 세지 않는다" {
            fun saveSet(weekStartedOn: LocalDate, matchingType: MatchingType) = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = weekStartedOn.atStartOfDay(),
                    endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
                    matchingType = matchingType,
                ),
            )
            val thisWeek = LocalDate.of(2026, 11, 16)
            listOf(
                saveSet(thisWeek, MatchingType.GROUP),
                saveSet(thisWeek, MatchingType.ONE_TO_ONE),
                saveSet(thisWeek.minusWeeks(1), MatchingType.ONE_TO_ONE),
            ).forEach { set ->
                quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = set.id, totalCount = 1).apply { recordAnswer() })
            }
            val stopped = saveSet(thisWeek.minusWeeks(2), MatchingType.ONE_TO_ONE)
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = stopped.id, totalCount = 2).apply { recordAnswer() })

            quizSetRepository.countCompletedWeeksOf(1L) shouldBe 2L
            quizSetRepository.countCompletedWeeksOf(2L) shouldBe 0L
        }
    }
})
