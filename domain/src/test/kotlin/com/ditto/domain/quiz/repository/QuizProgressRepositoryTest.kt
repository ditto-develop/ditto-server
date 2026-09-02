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

    "findLatestQuizSetIdCompletedByBoth" - {
        "둘 다 완주한 셋 중 가장 최근 것을 반환한다" {
            val older = quizSetRepository.save(QuizSetFixture.create(category = "성격"))
            val latest = quizSetRepository.save(QuizSetFixture.create(category = "취미"))
            saveCompleted(quizProgressRepository, memberId = 1L, quizSetId = older.id)
            saveCompleted(quizProgressRepository, memberId = 2L, quizSetId = older.id)
            saveCompleted(quizProgressRepository, memberId = 1L, quizSetId = latest.id)
            saveCompleted(quizProgressRepository, memberId = 2L, quizSetId = latest.id)

            quizProgressRepository.findLatestQuizSetIdCompletedByBoth(1L, 2L) shouldBe latest.id
        }

        "한쪽만 완주한 셋은 고르지 않는다" {
            val both = quizSetRepository.save(QuizSetFixture.create(category = "성격"))
            val mineOnly = quizSetRepository.save(QuizSetFixture.create(category = "취미"))
            saveCompleted(quizProgressRepository, memberId = 1L, quizSetId = both.id)
            saveCompleted(quizProgressRepository, memberId = 2L, quizSetId = both.id)
            saveCompleted(quizProgressRepository, memberId = 1L, quizSetId = mineOnly.id)

            quizProgressRepository.findLatestQuizSetIdCompletedByBoth(1L, 2L) shouldBe both.id
        }

        "상대가 진행 중(미완주)이면 그 셋도 제외한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            saveCompleted(quizProgressRepository, memberId = 1L, quizSetId = quizSet.id)
            // 상대는 1문항만 답해 IN_PROGRESS 로 남는다.
            val inProgress = QuizProgressFixture.create(memberId = 2L, quizSetId = quizSet.id, totalCount = 2)
            inProgress.recordAnswer()
            quizProgressRepository.save(inProgress)

            quizProgressRepository.findLatestQuizSetIdCompletedByBoth(1L, 2L) shouldBe null
        }

        "함께 완주한 셋이 없으면 null을 반환한다" {
            quizProgressRepository.findLatestQuizSetIdCompletedByBoth(1L, 2L) shouldBe null
        }
    }
})

/** 완주(COMPLETED) 상태의 진행 하나를 만든다 — 1문항짜리로 두고 곧바로 답변시킨다. */
private fun saveCompleted(
    quizProgressRepository: QuizProgressRepository,
    memberId: Long,
    quizSetId: Long,
) {
    val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
    progress.recordAnswer()
    quizProgressRepository.save(progress)
}
