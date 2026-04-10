package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class QuizChoiceTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "QuizChoice 생성" - {
        "QuizChoice를 생성하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(
                QuizChoiceFixture.create(quizId = quiz.id, content = "짜장면", displayOrder = 1),
            )

            choice.id shouldNotBe 0L
            choice.quizId shouldBe quiz.id
            choice.content shouldBe "짜장면"
            choice.displayOrder shouldBe 1
        }

        "한 퀴즈에 선택지 2개를 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "짜장면", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "짬뽕", displayOrder = 2))

            val choices = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(listOf(quiz.id))

            choices.size shouldBe 2
            choices[0].content shouldBe "짜장면"
            choices[1].content shouldBe "짬뽕"
        }
    }
})
