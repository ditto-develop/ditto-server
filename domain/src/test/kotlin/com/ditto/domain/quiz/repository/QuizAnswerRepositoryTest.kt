package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class QuizAnswerRepositoryTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "deleteByMemberIdAndQuizIds" - {
        "해당 memberId와 quizIds의 답변이 벌크 삭제된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
            val quiz2 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id))
            val choice2 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id))

            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = 1L, quizId = quiz1.id, choiceId = choice1.id))
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = 1L, quizId = quiz2.id, choiceId = choice2.id))

            quizAnswerRepository.deleteByMemberIdAndQuizIds(1L, listOf(quiz1.id, quiz2.id))

            quizAnswerRepository.findAll().size shouldBe 0
        }

        "다른 memberId의 답변은 삭제되지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice.id))
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = 2L, quizId = quiz.id, choiceId = choice.id))

            quizAnswerRepository.deleteByMemberIdAndQuizIds(1L, listOf(quiz.id))

            val remaining = quizAnswerRepository.findAll()
            remaining.size shouldBe 1
            remaining[0].memberId shouldBe 2L
        }

        "빈 quizIds로 호출해도 예외가 발생하지 않는다" {
            quizAnswerRepository.deleteByMemberIdAndQuizIds(1L, emptyList())

            quizAnswerRepository.findAll().size shouldBe 0
        }
    }
})
