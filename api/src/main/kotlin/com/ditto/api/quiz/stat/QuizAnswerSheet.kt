package com.ditto.api.quiz.stat

import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import org.springframework.stereotype.Component

/**
 * 퀴즈셋 문항·선택지와 몇몇 회원의 답을 한 번에 읽은 묶음. 기록·답변 비교·그룹 분포·힌트·질문 카드가 같은 모양으로 쓴다.
 * 문구는 지금 값이다(문항 문구는 최신 값, 숫자는 스냅샷 값 — `docs/domains/quiz.md`).
 */
class QuizAnswerSheet(
    /** 표시 순서 */
    val quizzes: List<Quiz>,
    private val choicesByQuizId: Map<Long, List<QuizChoice>>,
    private val choiceIdByMemberAndQuiz: Map<Long, Map<Long, Long>>,
) {
    fun choicesOf(quizId: Long): List<QuizChoice> = choicesByQuizId[quizId].orEmpty()

    fun choiceOf(memberId: Long, quizId: Long): Long? = choiceIdByMemberAndQuiz[memberId]?.get(quizId)

    fun quiz(quizId: Long): Quiz? = quizzes.find { it.id == quizId }

    /** [memberIds] 중 이 문항에 답한 사람들의 선택지별 인원. 아무도 고르지 않은 선택지는 0. */
    fun countsAmong(quizId: Long, memberIds: Collection<Long>): Map<Long, Int> {
        val picked = memberIds.mapNotNull { choiceOf(it, quizId) }.groupingBy { it }.eachCount()
        return choicesOf(quizId).associate { it.id to (picked[it.id] ?: 0) }
    }
}

@Component
class QuizAnswerSheetLoader(
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
) {
    fun load(quizSetId: Long, memberIds: Collection<Long>): QuizAnswerSheet {
        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
        val quizIds = quizzes.map { it.id }
        if (quizIds.isEmpty()) return QuizAnswerSheet(emptyList(), emptyMap(), emptyMap())

        val choices = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(quizIds).groupBy { it.quizId }
        val answers = if (memberIds.isEmpty()) {
            emptyMap()
        } else {
            quizAnswerRepository.findByMemberIdInAndQuizIdIn(memberIds.toList(), quizIds)
                .groupBy { it.memberId }
                .mapValues { (_, rows) -> rows.associate { it.quizId to it.choiceId } }
        }
        return QuizAnswerSheet(quizzes, choices, answers)
    }
}
