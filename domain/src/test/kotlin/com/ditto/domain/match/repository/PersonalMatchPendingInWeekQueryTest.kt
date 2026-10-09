package com.ditto.domain.match.repository

import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

class PersonalMatchPendingInWeekQueryTest(
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val thisWeekStartedOn = LocalDate.of(2026, 6, 1)

    fun saveQuizSet(weekStartedOn: LocalDate = thisWeekStartedOn): Long =
        quizSetRepository.save(
            QuizSetFixture.create(
                startDate = weekStartedOn.atStartOfDay(),
                endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            ),
        ).id

    fun saveMatch(quizSetId: Long, receiverId: Long = 20L, status: PersonalMatchStatus = PersonalMatchStatus.PENDING) {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 10L,
                receiverId = receiverId,
                quizSetId = quizSetId,
                status = status,
            ),
        )
    }

    "이번 주 대기 신청은 보낸 쪽과 받은 쪽 모두에서 찾는다" {
        saveMatch(saveQuizSet())

        personalMatchRepository.existsPendingOfMemberInWeek(10L, thisWeekStartedOn) shouldBe true
        personalMatchRepository.existsPendingOfMemberInWeek(20L, thisWeekStartedOn) shouldBe true
    }

    "지난 주 대기 신청은 찾지 않는다" {
        saveMatch(saveQuizSet(weekStartedOn = thisWeekStartedOn.minusWeeks(1)))

        personalMatchRepository.existsPendingOfMemberInWeek(10L, thisWeekStartedOn) shouldBe false
    }

    "이번 주라도 대기가 아닌 신청은 찾지 않는다" {
        val quizSetId = saveQuizSet()
        saveMatch(quizSetId, receiverId = 20L, status = PersonalMatchStatus.ACCEPTED)
        saveMatch(quizSetId, receiverId = 30L, status = PersonalMatchStatus.REJECTED)

        personalMatchRepository.existsPendingOfMemberInWeek(10L, thisWeekStartedOn) shouldBe false
    }

    "신청에 끼지 않은 회원은 찾지 않는다" {
        saveMatch(saveQuizSet())

        personalMatchRepository.existsPendingOfMemberInWeek(99L, thisWeekStartedOn) shouldBe false
    }
})
