package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class QuizProgressRepositoryTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "deleteByMemberIdAndQuizSetIds" - {
        "해당 memberId와 quizSetIds의 진행 상태가 벌크 삭제된다" {
            val quizSet1 = quizSetRepository.save(QuizSetFixture.create(category = "성격"))
            val quizSet2 = quizSetRepository.save(QuizSetFixture.create(category = "취미"))

            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet1.id))
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet2.id))

            quizProgressRepository.deleteByMemberIdAndQuizSetIds(1L, listOf(quizSet1.id, quizSet2.id))

            quizProgressRepository.findAll().size shouldBe 0
        }

        "다른 memberId의 진행 상태는 삭제되지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())

            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id))
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 2L, quizSetId = quizSet.id))

            quizProgressRepository.deleteByMemberIdAndQuizSetIds(1L, listOf(quizSet.id))

            val remaining = quizProgressRepository.findAll()
            remaining.size shouldBe 1
            remaining[0].memberId shouldBe 2L
        }

        "빈 quizSetIds로 호출해도 예외가 발생하지 않는다" {
            quizProgressRepository.deleteByMemberIdAndQuizSetIds(1L, emptyList())

            quizProgressRepository.findAll().size shouldBe 0
        }
    }
})
