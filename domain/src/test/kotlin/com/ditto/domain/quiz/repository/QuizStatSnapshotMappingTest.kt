package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizChoiceStat
import com.ditto.domain.quiz.entity.QuizMemberStat
import com.ditto.domain.quiz.entity.QuizSetStat
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataIntegrityViolationException
import javax.sql.DataSource

class QuizStatSnapshotMappingTest(
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizChoiceStatRepository: QuizChoiceStatRepository,
    private val quizMemberStatRepository: QuizMemberStatRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "스냅샷 세 행을 저장하고 셋·회원으로 다시 읽는다" {
        quizSetStatRepository.save(QuizSetStat.of(quizSetId = 1L, participantCount = 20, overlapThreshold = 8))
        quizChoiceStatRepository.saveAll(
            listOf(
                QuizChoiceStat.of(quizSetId = 1L, quizId = 10L, choiceId = 101L, selectCount = 19, percent = 95),
                QuizChoiceStat.of(quizSetId = 1L, quizId = 10L, choiceId = 102L, selectCount = 1, percent = 5),
            ),
        )
        quizMemberStatRepository.save(QuizMemberStat.of(quizSetId = 1L, memberId = 7L, minorityCount = 1, overlapCount = 12))

        quizSetStatRepository.findByQuizSetId(1L)!!.overlapThreshold shouldBe 8
        quizChoiceStatRepository.findByQuizSetId(1L).map { it.choiceId to it.percent } shouldBe listOf(101L to 95, 102L to 5)
        quizMemberStatRepository.findByQuizSetIdAndMemberId(1L, 7L)!!.let {
            it.minorityCount shouldBe 1
            it.overlapCount shouldBe 12
        }
        quizMemberStatRepository.findByMemberIdAndQuizSetIdIn(7L, listOf(1L, 2L)).size shouldBe 1
        quizMemberStatRepository.findByQuizSetId(1L).single().memberId shouldBe 7L
    }

    "한 셋의 스냅샷 머리 행은 하나뿐이다 — 동시에 만들면 한쪽이 진다" {
        quizSetStatRepository.saveAndFlush(QuizSetStat.of(quizSetId = 1L, participantCount = 1, overlapThreshold = 1))

        shouldThrow<DataIntegrityViolationException> {
            quizSetStatRepository.saveAndFlush(QuizSetStat.of(quizSetId = 1L, participantCount = 2, overlapThreshold = 1))
        }
    }
})
