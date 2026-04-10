package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class QuizSetTest(
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "QuizSet 생성" - {
        "QuizSet을 생성하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())

            quizSet.id shouldNotBe 0L
            quizSet.year shouldBe 2026
            quizSet.month shouldBe 4
            quizSet.week shouldBe 2
            quizSet.category shouldBe "성격"
            quizSet.title shouldBe "이번 주 1:1 매칭"
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
