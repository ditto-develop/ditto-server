package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizSetTest(
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "QuizSet 생성" - {
        "QuizSet을 생성하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())

            quizSet.id shouldNotBe 0L
            quizSet.category shouldBe "성격"
            quizSet.title shouldBe "이번 주 1:1 매칭"
            quizSet.weekStartedOn shouldBe LocalDate.of(2026, 4, 6)
        }

        "기본 상태는 비활성이다" {
            val quizSet = QuizSetFixture.create(isActive = false)

            quizSet.isActive shouldBe false
        }

        "기본 매칭 타입은 ONE_TO_ONE이다" {
            val quizSet = QuizSetFixture.create()

            quizSet.matchingType shouldBe MatchingType.ONE_TO_ONE
        }
    }

    "운영 주 시작일(weekStartedOn)" - {
        "주 중간 시작일이면 시작일이 속한 주의 월요일로 파생된다" {
            val quizSet = QuizSetFixture.create(startDate = LocalDateTime.of(2026, 4, 8, 14, 0))

            quizSet.weekStartedOn shouldBe LocalDate.of(2026, 4, 6)
        }

        "월 경계 주의 시작일도 그 주 월요일로 파생된다 (2026-08-01 → 2026-07-27)" {
            val quizSet = QuizSetFixture.create(startDate = LocalDateTime.of(2026, 8, 1, 0, 0))

            quizSet.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
        }

        "같은 주의 복수 퀴즈셋은 같은 weekStartedOn을 가진 채 함께 저장될 수 있다" {
            val first = quizSetRepository.save(QuizSetFixture.create(startDate = LocalDateTime.of(2026, 7, 27, 0, 0)))
            val second = quizSetRepository.save(QuizSetFixture.create(startDate = LocalDateTime.of(2026, 7, 29, 0, 0)))

            first.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
            second.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
        }

        "다른 주의 퀴즈셋은 다른 weekStartedOn을 가진다" {
            val thisWeek = QuizSetFixture.create(startDate = LocalDateTime.of(2026, 7, 27, 0, 0))
            val nextWeek = QuizSetFixture.create(startDate = LocalDateTime.of(2026, 8, 3, 0, 0))

            thisWeek.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
            nextWeek.weekStartedOn shouldBe LocalDate.of(2026, 8, 3)
        }
    }

    "QuizSet 활성화" - {
        "activate() 호출 시 활성화된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = false))

            quizSet.activate()
            quizSetRepository.save(quizSet)

            val found = quizSetRepository.findById(quizSet.id).get()
            found.isActive shouldBe true
        }

        "deactivate() 호출 시 비활성화된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = true))

            quizSet.deactivate()
            quizSetRepository.save(quizSet)

            val found = quizSetRepository.findById(quizSet.id).get()
            found.isActive shouldBe false
        }
    }
})
