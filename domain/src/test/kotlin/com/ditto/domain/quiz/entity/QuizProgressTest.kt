package com.ditto.domain.quiz.entity

import com.ditto.domain.member.entity.GenderPreference
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
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

        "완주하는 답의 시각이 완주 시각으로 남는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 2),
            )
            val firstAnsweredAt = LocalDateTime.of(2026, 11, 16, 10, 0)
            val lastAnsweredAt = LocalDateTime.of(2026, 11, 20, 19, 59, 59)

            progress.recordAnswer(firstAnsweredAt)
            progress.completedAt shouldBe null

            progress.recordAnswer(lastAnsweredAt)
            quizProgressRepository.saveAndFlush(progress)

            val found = quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSet.id)
            found!!.completedAt shouldBe lastAnsweredAt
        }

        "시각 없이 완주하면 완주 시각은 비어 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 1),
            )

            progress.recordAnswer()

            progress.status shouldBe QuizProgressStatus.COMPLETED
            progress.completedAt shouldBe null
        }
    }

    "마감까지 완주했는지" - {
        val deadline = LocalDateTime.of(2026, 11, 18, 23, 59, 59)

        fun progressAnsweredAt(answeredAt: LocalDateTime?, totalCount: Int = 1): QuizProgress =
            QuizProgressFixture.create(memberId = 1L, quizSetId = 1L, totalCount = totalCount)
                .apply { recordAnswer(answeredAt) }

        "마감 시각까지 완주했으면 참이다" {
            progressAnsweredAt(deadline).isCompletedOnTime(deadline) shouldBe true
            progressAnsweredAt(deadline.minusDays(1)).isCompletedOnTime(deadline) shouldBe true
        }

        "마감 뒤에 완주했으면 거짓이다" {
            progressAnsweredAt(deadline.plusSeconds(1)).isCompletedOnTime(deadline) shouldBe false
        }

        "완주 시각이 없으면 마감 전에 완주한 것으로 본다" {
            progressAnsweredAt(null).isCompletedOnTime(deadline) shouldBe true
        }

        "완주하지 않았으면 거짓이다" {
            progressAnsweredAt(deadline.minusDays(1), totalCount = 2).isCompletedOnTime(deadline) shouldBe false
        }
    }

    "마감 뒤에 완주했는지" - {
        val deadline = LocalDateTime.of(2026, 11, 18, 23, 59, 59)

        fun progressAnsweredAt(answeredAt: LocalDateTime?, totalCount: Int = 1): QuizProgress =
            QuizProgressFixture.create(memberId = 1L, quizSetId = 1L, totalCount = totalCount)
                .apply { recordAnswer(answeredAt) }

        "마감 뒤에 완주했으면 참이다" {
            progressAnsweredAt(deadline.plusSeconds(1)).isCompletedLate(deadline) shouldBe true
        }

        "마감까지 완주했거나 완주 시각이 없으면 거짓이다" {
            progressAnsweredAt(deadline).isCompletedLate(deadline) shouldBe false
            progressAnsweredAt(null).isCompletedLate(deadline) shouldBe false
        }

        "완주하지 않았으면 거짓이다" {
            progressAnsweredAt(deadline.plusSeconds(1), totalCount = 2).isCompletedLate(deadline) shouldBe false
        }
    }

    "시각 전에 완주했는지" - {
        val roundStartsAt = LocalDateTime.of(2026, 11, 20, 20, 0)

        fun progressAnsweredAt(answeredAt: LocalDateTime?, totalCount: Int = 1): QuizProgress =
            QuizProgressFixture.create(memberId = 1L, quizSetId = 1L, totalCount = totalCount)
                .apply { recordAnswer(answeredAt) }

        "그 시각 전에 완주했으면 참이고, 그 시각 정각이나 뒤면 거짓이다" {
            progressAnsweredAt(roundStartsAt.minusSeconds(1)).isCompletedBefore(roundStartsAt) shouldBe true
            progressAnsweredAt(roundStartsAt).isCompletedBefore(roundStartsAt) shouldBe false
        }

        "완주 시각이 없으면 참이고, 완주하지 않았으면 거짓이다" {
            progressAnsweredAt(null).isCompletedBefore(roundStartsAt) shouldBe true
            progressAnsweredAt(roundStartsAt.minusDays(1), totalCount = 2).isCompletedBefore(roundStartsAt) shouldBe false
        }
    }

    "매칭 성별 선호" - {
        "기본값은 OPPOSITE(이성)이다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id),
            )

            progress.preferredGender shouldBe GenderPreference.OPPOSITE
        }

        "selectPreferredGender 로 선호를 변경하고 저장할 수 있다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val progress = quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id),
            )

            progress.selectPreferredGender(GenderPreference.SAME)
            quizProgressRepository.saveAndFlush(progress)

            val found = quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSet.id)
            found!!.preferredGender shouldBe GenderPreference.SAME
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
