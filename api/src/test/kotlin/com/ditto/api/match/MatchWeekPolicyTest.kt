package com.ditto.api.match

import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

class MatchWeekPolicyTest(
    private val matchWeekPolicy: MatchWeekPolicy,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val lastWeekMonday = OperationWeek.containing(LocalDate.now()).startedOn.minusWeeks(1)

    fun saveLastWeekQuizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE): QuizSet =
        quizSetRepository.save(
            QuizSetFixture.create(
                startDate = lastWeekMonday.atStartOfDay(),
                endDate = lastWeekMonday.plusDays(2).atTime(23, 59, 59),
                matchingType = matchingType,
            ),
        )

    fun complete(memberId: Long, quizSetId: Long) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
        progress.recordAnswer() // NOT_STARTED -> COMPLETED
        quizProgressRepository.save(progress)
    }

    "findCompletedQuizSet" - {

        "이번 주에 완주한 해당 타입 퀴즈셋을 반환한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
            complete(memberId = 1L, quizSetId = quizSet.id)

            matchWeekPolicy.findCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)?.id shouldBe quizSet.id
        }

        "지난 주에 완주한 퀴즈셋만 있으면 null 이다 — 지난 사이클 후보가 딸려 오지 않게 한다" {
            val lastWeek = saveLastWeekQuizSet()
            complete(memberId = 1L, quizSetId = lastWeek.id)

            matchWeekPolicy.findCompletedQuizSet(1L, MatchingType.ONE_TO_ONE) shouldBe null
        }

        "1:1과 그룹은 서로 독립이다 — 한쪽을 풀었다고 다른 쪽이 열리지 않는다" {
            val oneToOne = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.ONE_TO_ONE))
            complete(memberId = 1L, quizSetId = oneToOne.id)

            matchWeekPolicy.findCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)?.id shouldBe oneToOne.id
            matchWeekPolicy.findCompletedQuizSet(1L, MatchingType.GROUP) shouldBe null
        }
    }

    "validateCurrentWeek" - {

        "이번 주 퀴즈셋이면 통과한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())

            matchWeekPolicy.validateCurrentWeek(quizSet.id)
        }

        "지난 주 퀴즈셋이면 NOT_MATCHING_PERIOD 다" {
            val lastWeek = saveLastWeekQuizSet()

            val exception = shouldThrow<WarnException> { matchWeekPolicy.validateCurrentWeek(lastWeek.id) }

            exception.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
        }

        "없는 퀴즈셋이면 NOT_FOUND 다" {
            val exception = shouldThrow<WarnException> { matchWeekPolicy.validateCurrentWeek(9999L) }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
