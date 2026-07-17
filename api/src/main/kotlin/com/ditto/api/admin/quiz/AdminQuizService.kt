package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 어드민 퀴즈셋 CRUD. 퀴즈셋과 하위 Quiz/QuizChoice 를 함께 관리한다(JPA cascade 미사용 → 수동 정리).
 */
@Service
@Transactional
class AdminQuizService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
) {
    @Transactional(readOnly = true)
    fun listQuizSets(): List<QuizSet> = quizSetRepository.findAllByOrderByYearDescMonthDescWeekDescIdDesc()

    @Transactional(readOnly = true)
    fun getQuizSet(id: Long): QuizSet =
        quizSetRepository.findById(id).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

    @Transactional(readOnly = true)
    fun getQuizzes(quizSetId: Long): List<Quiz> =
        quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)

    @Transactional(readOnly = true)
    fun getChoices(quizId: Long): List<QuizChoice> =
        quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quizId)

    fun createQuizSet(form: QuizSetForm): QuizSet {
        val quizSet = QuizSet.create(
            year = form.year,
            month = form.month,
            week = form.week,
            category = form.category,
            title = form.title,
            description = form.description,
            startDate = form.requiredStartDate(),
            endDate = form.requiredEndDate(),
            isActive = form.isActive,
            matchingType = form.matchingType,
        )
        return quizSetRepository.save(quizSet)
    }

    fun updateQuizSet(id: Long, form: QuizSetForm) {
        val quizSet = getQuizSet(id)
        quizSet.update(
            category = form.category,
            title = form.title,
            description = form.description,
            startDate = form.requiredStartDate(),
            endDate = form.requiredEndDate(),
            matchingType = form.matchingType,
        )
        if (form.isActive) quizSet.activate() else quizSet.deactivate()
    }

    fun activate(id: Long) {
        getQuizSet(id).activate()
    }

    fun deactivate(id: Long) {
        getQuizSet(id).deactivate()
    }

    fun deleteQuizSet(id: Long) {
        val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(id).map { it.id }
        if (quizIds.isNotEmpty()) quizChoiceRepository.deleteByQuizIdIn(quizIds)
        quizRepository.deleteByQuizSetId(id)
        quizSetRepository.deleteById(id)
    }

    fun addQuiz(quizSetId: Long, question: String, displayOrder: Int): Quiz {
        getQuizSet(quizSetId) // 존재 검증
        return quizRepository.save(Quiz.create(quizSetId = quizSetId, question = question, displayOrder = displayOrder))
    }

    fun updateQuiz(quizId: Long, question: String, displayOrder: Int) {
        val quiz = quizRepository.findById(quizId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        quiz.update(question, displayOrder)
    }

    fun deleteQuiz(quizId: Long) {
        quizChoiceRepository.deleteByQuizId(quizId)
        quizRepository.deleteById(quizId)
    }

    fun addChoice(quizId: Long, content: String, displayOrder: Int): QuizChoice =
        quizChoiceRepository.save(QuizChoice.create(quizId = quizId, content = content, displayOrder = displayOrder))

    fun updateChoice(choiceId: Long, content: String, displayOrder: Int) {
        val choice = quizChoiceRepository.findById(choiceId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        choice.update(content, displayOrder)
    }

    fun deleteChoice(choiceId: Long) {
        quizChoiceRepository.deleteById(choiceId)
    }
}
