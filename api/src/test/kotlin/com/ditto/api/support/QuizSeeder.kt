package com.ditto.api.support

import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 문항 2지선다 퀴즈셋과 회원 답을 심는 테스트 도우미. 통계·기록·답변 비교·힌트·질문 카드 테스트가 같은 모양을 쓴다.
 * 선택지 이름은 "Q{순서}-{1|2}" 이다.
 */
class QuizSeeder(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
) {
    fun quizSet(
        weekStartedOn: LocalDate = CLOSED_WEEK,
        questions: Int = 3,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        title: String = "시간이 멈춘다면",
        hintQuestions: List<String?> = List(questions) { null },
    ): SeededQuizSet {
        val set = quizSetRepository.save(
            QuizSetFixture.create(
                title = title,
                startDate = weekStartedOn.atStartOfDay(),
                endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
                matchingType = matchingType,
            ),
        )
        val quizzes = (1..questions).map { order ->
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = set.id, question = "Q$order", displayOrder = order, hintQuestion = hintQuestions[order - 1]),
            )
            quiz.id to (1..2).map { c ->
                quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "Q$order-$c", displayOrder = c)).id
            }
        }
        return SeededQuizSet(set.id, quizzes.map { it.first }, quizzes.map { it.second })
    }

    /** [picks]는 문항마다 고른 선택지 번호(1·2). 모두 답하면 완주(COMPLETED)다. */
    fun answer(memberId: Long, set: SeededQuizSet, picks: List<Int>) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = set.quizSetId, totalCount = set.quizIds.size)
        picks.forEachIndexed { i, pick ->
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = set.quizIds[i], choiceId = set.choiceIds[i][pick - 1]))
            progress.recordAnswer()
        }
        quizProgressRepository.save(progress)
    }

    companion object {
        /** 이미 마감된 주(월). 결과가 공개된 상태를 만든다. */
        val CLOSED_WEEK: LocalDate = LocalDate.of(2026, 10, 26)

        /** [CLOSED_WEEK] 마감 직후. */
        val AFTER_CLOSE: LocalDateTime = LocalDateTime.of(2026, 10, 29, 9, 0)
    }
}

data class SeededQuizSet(
    val quizSetId: Long,
    val quizIds: List<Long>,
    /** 문항마다 선택지 1·2 의 ID */
    val choiceIds: List<List<Long>>,
) {
    fun choice(order: Int, pick: Int): Long = choiceIds[order - 1][pick - 1]
}
