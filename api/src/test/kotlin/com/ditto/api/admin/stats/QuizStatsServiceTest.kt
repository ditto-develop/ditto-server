package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

private val TODAY = LocalDate.of(2026, 10, 22)
private val WEEK = OperationWeek.containing(TODAY)

class QuizStatsServiceTest(
    private val quizStatsService: QuizStatsService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveQuizSet(
        week: OperationWeek,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        isActive: Boolean = true,
    ): QuizSet = quizSetRepository.save(
        QuizSetFixture.create(
            startDate = week.dateTimeRange.start,
            endDate = week.startedOn.plusDays(2).atTime(23, 59, 59),
            matchingType = matchingType,
            isActive = isActive,
        ),
    )

    fun saveCompletedProgress(nickname: String, quizSet: QuizSet) {
        val member = memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )
        quizProgressRepository.save(
            QuizProgressFixture.create(memberId = member.id, quizSetId = quizSet.id, totalCount = 1)
                .apply { recordAnswer() },
        )
    }

    "퀴즈 참여 원본" - {
        "활성 회원 회차는 고른 회차까지 활성 셋이 있는 주 최근 4개다" {
            (0L..5L).forEach { weeksAgo -> saveQuizSet(WEEK.minusWeeks(weeksAgo)) }
            saveQuizSet(WEEK.minusWeeks(1), matchingType = MatchingType.GROUP)
            saveQuizSet(WEEK.minusWeeks(6), isActive = false)
            saveQuizSet(OperationWeek(WEEK.startedOn.plusWeeks(1)))

            val stats = quizStatsService.findStats(StatsFilter(), WEEK.minusWeeks(1), TODAY)

            stats.activeRoundWeeks shouldContainExactly (1L..4L).map { WEEK.minusWeeks(it).startedOn }
        }

        "매칭 유형을 고르면 참여와 활성 회원 모두 그 유형만 센다" {
            saveCompletedProgress("one", saveQuizSet(WEEK))
            saveCompletedProgress("group", saveQuizSet(WEEK, matchingType = MatchingType.GROUP))

            val stats = quizStatsService.findStats(StatsFilter(matchingType = MatchingTypeFilter.GROUP), WEEK, TODAY)

            stats.progressCounts.map { it.matchingType } shouldContainExactly listOf(MatchingType.GROUP)
            stats.activeMemberCount shouldBe 1L
        }
    }
})
