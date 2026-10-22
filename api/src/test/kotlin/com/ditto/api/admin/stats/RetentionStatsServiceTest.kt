package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.RetentionStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.repository.querydsl.MatchingTypeMemberCount
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

private val TODAY = LocalDate.of(2026, 10, 22)
private val WEEK = OperationWeek.containing(TODAY)

class RetentionStatsServiceTest(
    private val retentionStatsService: RetentionStatsService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0

    fun saveQuizSet(week: OperationWeek, matchingType: MatchingType = MatchingType.ONE_TO_ONE): QuizSet =
        quizSetRepository.save(
            QuizSetFixture.create(
                startDate = week.dateTimeRange.start,
                endDate = week.startedOn.plusDays(2).atTime(23, 59, 59),
                matchingType = matchingType,
            ),
        )

    fun saveMemberCompleting(vararg quizSets: QuizSet) {
        memberIndex += 1
        val nickname = "m$memberIndex"
        val member = memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics"))
        quizSets.forEach { quizSet ->
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = member.id, quizSetId = quizSet.id, totalCount = 1)
                    .apply { recordAnswer() },
            )
        }
    }

    "리텐션 원본" - {
        "지난 회차는 퀴즈를 쉰 주를 건너뛴 바로 앞 회차다" {
            val twoWeeksAgo = saveQuizSet(WEEK.minusWeeks(2))
            val thisWeek = saveQuizSet(WEEK)
            saveMemberCompleting(twoWeeksAgo, thisWeek)
            saveMemberCompleting(twoWeeksAgo)

            val stats = retentionStatsService.findStats(StatsFilter(), WEEK, TODAY)

            stats.previousRoundWeek shouldBe WEEK.minusWeeks(2).startedOn
            stats.completedAgainCounts shouldContainExactly listOf(MatchingTypeMemberCount(MatchingType.ONE_TO_ONE, 1L))
        }

        "앞에 회차가 없으면 비어 있다" {
            saveQuizSet(WEEK)

            retentionStatsService.findStats(StatsFilter(), WEEK, TODAY) shouldBe RetentionStatsData.NO_PREVIOUS_ROUND
        }

        "매칭 유형을 고르면 지난 회차에 그 유형을 푼 회원만 센다" {
            val previousOneToOne = saveQuizSet(WEEK.minusWeeks(1))
            val previousGroup = saveQuizSet(WEEK.minusWeeks(1), MatchingType.GROUP)
            val thisWeek = saveQuizSet(WEEK)
            saveMemberCompleting(previousOneToOne, thisWeek)
            saveMemberCompleting(previousGroup, thisWeek)

            val groupOnly = StatsFilter(matchingType = MatchingTypeFilter.GROUP)

            val stats = retentionStatsService.findStats(groupOnly, WEEK, TODAY)

            stats.completedAgainCounts shouldContainExactly listOf(MatchingTypeMemberCount(MatchingType.GROUP, 1L))
            stats.previousProgressCounts.map { it.matchingType } shouldContainExactly listOf(MatchingType.GROUP)
        }
    }
})
