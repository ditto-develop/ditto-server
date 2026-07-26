package com.ditto.api.quiz.service

import com.ditto.api.quiz.dto.CurrentWeekQuizSetResponse
import com.ditto.api.quiz.dto.CurrentWeekQuizSetsResponse
import com.ditto.api.quiz.dto.QuizSetResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class QuizSetService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
) {
    fun getCurrentWeekQuizSets(now: LocalDateTime): CurrentWeekQuizSetsResponse {
        val quizSets = quizSetRepository.findCurrentWeekActive(now)
        val currentWeek = OperationWeek.containing(now.toLocalDate())

        if (quizSets.isEmpty()) {
            return CurrentWeekQuizSetsResponse.empty(currentWeek)
        }
        return toCurrentWeekResponse(currentWeek, quizSets)
    }

    fun getQuizSet(id: Long): QuizSetResponse {
        val quizSet =
            quizSetRepository
                .findById(id)
                .orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

        return QuizSetResponse.from(quizSet)
    }

    private fun toCurrentWeekResponse(
        currentWeek: OperationWeek,
        quizSets: List<QuizSet>,
    ): CurrentWeekQuizSetsResponse {
        val quizzesByQuizSetId = findQuizzesByQuizSetId(quizSets.map { it.id })
        val allQuizIds = quizzesByQuizSetId.values.flatten().map { it.id }
        val choicesByQuizId = findChoicesByQuizId(allQuizIds)

        return CurrentWeekQuizSetsResponse.of(
            currentWeek = currentWeek,
            quizSets =
                quizSets.map { quizSet ->
                    CurrentWeekQuizSetResponse.from(
                        quizSet = quizSet,
                        quizzes = quizzesByQuizSetId[quizSet.id] ?: emptyList(),
                        choicesByQuizId = choicesByQuizId,
                    )
                },
        )
    }

    private fun findQuizzesByQuizSetId(quizSetIds: List<Long>): Map<Long, List<Quiz>> =
        quizRepository
            .findByQuizSetIdInOrderByDisplayOrderAsc(quizSetIds)
            .groupBy { it.quizSetId }

    private fun findChoicesByQuizId(quizIds: List<Long>): Map<Long, List<QuizChoice>> {
        if (quizIds.isEmpty()) return emptyMap()
        return quizChoiceRepository
            .findByQuizIdInOrderByDisplayOrderAsc(quizIds)
            .groupBy { it.quizId }
    }
}
