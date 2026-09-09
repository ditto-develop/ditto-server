package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizForm
import com.ditto.api.admin.quiz.dto.QuizChoiceForm
import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
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
    private val quizAnswerRepository: QuizAnswerRepository,
) {
    @Transactional(readOnly = true)
    fun listQuizSets(): List<QuizSet> = quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc()

    @Transactional(readOnly = true)
    fun getQuizSet(id: Long): QuizSet =
        quizSetRepository.findById(id).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

    @Transactional(readOnly = true)
    fun getQuizzes(quizSetId: Long): List<Quiz> =
        quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)

    @Transactional(readOnly = true)
    fun getChoicesByQuizIds(quizIds: List<Long>): Map<Long, List<QuizChoice>> {
        if (quizIds.isEmpty()) return emptyMap()
        return quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(quizIds).groupBy { it.quizId }
    }

    @Transactional(readOnly = true)
    fun getAnswerCounts(quizIds: List<Long>): Map<Long, Long> = quizAnswerRepository.countAnswersPerQuiz(quizIds)

    fun createQuizSet(form: QuizSetForm): QuizSet {
        validatePeriodWithinOneWeek(form)
        val quizSet = QuizSet.create(
            category = form.category,
            title = form.title,
            description = form.description,
            startDate = form.requiredStartDate(),
            endDate = form.requiredEndDate(),
            isActive = form.isActive,
            matchingType = form.matchingType,
        )
        val saved = quizSetRepository.save(quizSet)
        saveQuizzes(saved.id, form.quizzes)
        return saved
    }

    fun updateQuizSet(id: Long, form: QuizSetForm) {
        validatePeriodWithinOneWeek(form)
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
        saveQuizzes(id, form.quizzes)
    }

    /**
     * 화면에 보이는 순서대로 문항·선택지를 한 번에 반영한다.
     * 답변이 달린 문항은 지우거나 선택지 수를 바꿀 수 없다. quiz_answer 가 quiz_id·choice_id 로만 연결돼
     * 지우면 답변이 매칭 점수에서 조용히 빠진다.
     */
    private fun saveQuizzes(quizSetId: Long, quizForms: List<QuizForm>) {
        val filledQuizForms = quizForms.filterNot { it.isEmpty() }
        if (filledQuizForms.isEmpty()) return
        validateNoBlankField(filledQuizForms)

        val existingQuizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
        val answerCountByQuizId = quizAnswerRepository.countAnswersPerQuiz(existingQuizzes.map { it.id })
        deleteRemovedQuizzes(existingQuizzes, filledQuizForms, answerCountByQuizId)

        filledQuizForms.forEachIndexed { index, quizForm ->
            val quiz = updateOrCreateQuiz(quizSetId, quizForm, displayOrder = index + 1)
            saveChoices(quiz.id, quizForm.choices, quizHasAnswers = answerCountByQuizId.containsKey(quiz.id))
        }
    }

    private fun validateNoBlankField(quizForms: List<QuizForm>) {
        quizForms.forEachIndexed { index, quizForm ->
            val blankField = quizForm.blankFieldName() ?: return@forEachIndexed
            throw WarnException(ErrorCode.BAD_REQUEST, "${index + 1}번 문항의 $blankField 항목이 비어 있습니다.")
        }
    }

    private fun deleteRemovedQuizzes(
        existingQuizzes: List<Quiz>,
        submittedQuizForms: List<QuizForm>,
        answerCountByQuizId: Map<Long, Long>,
    ) {
        val submittedQuizIds = submittedQuizForms.mapNotNull { it.id }.toSet()
        val removedQuizzes = existingQuizzes.filterNot { it.id in submittedQuizIds }
        if (removedQuizzes.isEmpty()) return

        val answeredQuizzes = removedQuizzes.filter { answerCountByQuizId.containsKey(it.id) }
        if (answeredQuizzes.isNotEmpty()) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "답변이 등록된 문항은 삭제할 수 없습니다: ${answeredQuizzes.joinToString { it.question }}",
            )
        }

        val removedQuizIds = removedQuizzes.map { it.id }
        quizChoiceRepository.deleteByQuizIdIn(removedQuizIds)
        quizRepository.deleteAllByIdInBatch(removedQuizIds)
    }

    private fun updateOrCreateQuiz(quizSetId: Long, quizForm: QuizForm, displayOrder: Int): Quiz {
        val quizId = quizForm.id
            ?: return quizRepository.save(
                Quiz.create(quizSetId = quizSetId, question = quizForm.question, displayOrder = displayOrder),
            )

        val quiz = quizRepository.findById(quizId)
            .orElseThrow { WarnException(ErrorCode.NOT_FOUND, "문항을 찾을 수 없습니다.") }
        if (quiz.quizSetId != quizSetId) {
            throw WarnException(ErrorCode.BAD_REQUEST, "다른 퀴즈셋의 문항은 수정할 수 없습니다.")
        }
        quiz.update(quizForm.question, displayOrder)
        return quiz
    }

    private fun saveChoices(quizId: Long, choiceForms: List<QuizChoiceForm>, quizHasAnswers: Boolean) {
        val existingChoices = quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quizId)
        val submittedChoiceIds = choiceForms.mapNotNull { it.id }.toSet()
        val removedChoiceIds = existingChoices.map { it.id }.filterNot { it in submittedChoiceIds }

        if (removedChoiceIds.isNotEmpty() && quizHasAnswers) {
            throw WarnException(ErrorCode.BAD_REQUEST, "답변이 등록된 문항의 선택지는 지울 수 없습니다.")
        }
        if (removedChoiceIds.isNotEmpty()) quizChoiceRepository.deleteAllByIdInBatch(removedChoiceIds)

        val existingChoiceById = existingChoices.associateBy { it.id }
        choiceForms.forEachIndexed { index, choiceForm ->
            val displayOrder = index + 1
            val choiceId = choiceForm.id
                ?: run {
                    quizChoiceRepository.save(
                        QuizChoice.create(quizId = quizId, content = choiceForm.content, displayOrder = displayOrder),
                    )
                    return@forEachIndexed
                }

            val choice = existingChoiceById[choiceId]
                ?: throw WarnException(ErrorCode.NOT_FOUND, "선택지를 찾을 수 없습니다.")
            choice.update(choiceForm.content, displayOrder)
        }
    }

    /** 기간이 두 운영 주에 걸치면 주간 식별자(weekStartedOn)와 실제 기간이 어긋나므로 유입 시점에 막는다. */
    private fun validatePeriodWithinOneWeek(form: QuizSetForm) {
        val startWeek = OperationWeek.containing(form.requiredStartDate().toLocalDate())
        val endWeek = OperationWeek.containing(form.requiredEndDate().toLocalDate())
        if (startWeek != endWeek) {
            throw WarnException(ErrorCode.BAD_REQUEST, "퀴즈셋 기간은 한 운영 주(월~일) 안에 있어야 합니다.")
        }
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

}
