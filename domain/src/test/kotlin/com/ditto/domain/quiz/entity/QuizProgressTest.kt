package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class QuizProgressTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "QuizProgress 생성" - {
        "QuizProgress를 생성하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 5),
            )

            progress.id shouldNotBe 0L
            progress.memberId shouldBe 1L
            progress.quizSetId shouldBe quizSet.id
            progress.totalCount shouldBe 5
            progress.answeredCount shouldBe 0
            progress.status shouldBe QuizProgressStatus.NOT_STARTED
        }

        "같은 memberId, quizSetId로 중복 저장하면 예외가 발생한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id),
            )

            shouldThrow<Exception> {
                quizProgressRepository.saveAndFlush(
                    QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id),
                )
            }
        }
    }

    "QuizProgress 상태 전이" - {
        "recordAnswer() 호출 시 IN_PROGRESS로 변경된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 3),
            )

            progress.recordAnswer()
            quizProgressRepository.saveAndFlush(progress)

            val found = quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSet.id)
            found shouldNotBe null
            found!!.answeredCount shouldBe 1
            found.status shouldBe QuizProgressStatus.IN_PROGRESS
        }

        "모든 퀴즈에 답변하면 COMPLETED로 변경된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 2),
            )

            progress.recordAnswer()
            progress.recordAnswer()
            quizProgressRepository.saveAndFlush(progress)

            val found = quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSet.id)
            found shouldNotBe null
            found!!.answeredCount shouldBe 2
            found.status shouldBe QuizProgressStatus.COMPLETED
        }
    }

    "QuizProgressRepository" - {
        "countByQuizSetIdInAndStatus로 완료한 사용자 수를 조회할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())

            val progress1 = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 1),
            )
            progress1.recordAnswer()
            quizProgressRepository.saveAndFlush(progress1)

            val progress2 = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 2L, quizSetId = quizSet.id, totalCount = 1),
            )
            progress2.recordAnswer()
            quizProgressRepository.saveAndFlush(progress2)

            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 3L, quizSetId = quizSet.id, totalCount = 2),
            )

            val count = quizProgressRepository.countByQuizSetIdInAndStatus(
                listOf(quizSet.id),
                QuizProgressStatus.COMPLETED,
            )
            count shouldBe 2
        }
    }
})
