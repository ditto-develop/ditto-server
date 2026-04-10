package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class QuizTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "Quiz 생성" - {
        "Quiz를 생성하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = quizSet.id, question = "짜장면 vs 짬뽕?", displayOrder = 1),
            )

            quiz.id shouldNotBe 0L
            quiz.quizSetId shouldBe quizSet.id
            quiz.question shouldBe "짜장면 vs 짬뽕?"
            quiz.displayOrder shouldBe 1
        }
    }
})
