package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizChoiceForm
import com.ditto.api.admin.quiz.dto.QuizForm
import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizResponsePeriod
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
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
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
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
    fun getAnswerCountsByQuizIds(quizIds: List<Long>): Map<Long, Long> =
        quizAnswerRepository.countAnswersPerQuiz(quizIds)

    fun createQuizSet(form: QuizSetForm): QuizSet {
        val period = QuizResponsePeriod(form.requiredWeek())
        val quizSet = QuizSet.create(
            category = form.category,
            title = form.title,
            description = form.description,
            startDate = period.startsAt,
            endDate = period.endsAt,
            isActive = form.isActive,
            matchingType = form.matchingType,
        )
        val saved = quizSetRepository.save(quizSet)
        saveQuizzes(saved.id, form.validatedQuizzes())
        return saved
    }

    /** 자유 입력 시절에 만든 퀴즈셋도 수정 저장 순간 기간이 월~수로 다시 맞춰진다. */
    fun updateQuizSet(id: Long, form: QuizSetForm) {
        val period = QuizResponsePeriod(form.requiredWeek())
        val quizSet = getQuizSet(id)
        quizSet.update(
            category = form.category,
            title = form.title,
            description = form.description,
            startDate = period.startsAt,
            endDate = period.endsAt,
            matchingType = form.matchingType,
        )
        if (form.isActive) quizSet.activate() else quizSet.deactivate()
        saveQuizzes(id, form.validatedQuizzes())
    }

    /**
     * 답변이 달린 문항은 지우거나 선택지 수를 바꿀 수 없다. quiz_answer 가 quiz_id·choice_id 로만 연결돼
     * 지우면 답변이 매칭 점수에서 조용히 빠진다.
     * 진행이 시작된 퀴즈셋은 문항 개수 자체를 바꿀 수 없다. quiz_progress.totalCount 가 첫 답변 시점에 굳어
     * 문항이 줄면 그 회원이 완주할 수 없다.
     */
    private fun saveQuizzes(quizSetId: Long, submittedQuizForms: List<QuizForm>) {
        if (submittedQuizForms.isEmpty()) return

        val existingQuizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
        validateNoQuizAddedOrRemovedWhenAnswered(quizSetId, existingQuizzes, submittedQuizForms)

        val answerCountByQuizId = quizAnswerRepository.countAnswersPerQuiz(existingQuizzes.map { it.id })
        deleteRemovedQuizzes(existingQuizzes, submittedQuizForms, answerCountByQuizId)

        val existingChoicesByQuizId = getChoicesByQuizIds(existingQuizzes.map { it.id })
        submittedQuizForms.forEachIndexed { index, quizForm ->
            val quiz = updateOrCreateQuiz(quizSetId, quizForm, displayOrder = index + 1)
            val existingChoices = existingChoicesByQuizId[quiz.id].orEmpty()

            if (answerCountByQuizId.containsKey(quiz.id) && quizForm.choices.size != existingChoices.size) {
                throw WarnException(ErrorCode.BAD_REQUEST, "답변이 등록된 문항의 선택지 수는 바꿀 수 없습니다.")
            }
            saveChoices(quiz.id, quizForm.choices, existingChoices)
        }
    }

    /**
     * 개수가 같아도 문항이 교체되면 막는다. 답변자와 이후 참여자가 서로 다른 문항 집합에 답하면
     * MatchScoreCalculator 의 quizId→choiceId 비교가 어긋난다.
     * quiz_progress 행은 첫 답변 때 생기므로, 행이 있으면 이미 누가 답한 것이다.
     */
    private fun validateNoQuizAddedOrRemovedWhenAnswered(
        quizSetId: Long,
        existingQuizzes: List<Quiz>,
        submittedQuizForms: List<QuizForm>,
    ) {
        val hasNewQuiz = submittedQuizForms.any { it.id == null }
        val submittedQuizIds = submittedQuizForms.mapNotNull { it.id }.toSet()
        val existingQuizIds = existingQuizzes.map { it.id }.toSet()
        if (!hasNewQuiz && submittedQuizIds == existingQuizIds) return

        val answeredByAnyone = quizProgressRepository.existsByQuizSetId(quizSetId)
        if (!answeredByAnyone) return

        throw WarnException(
            ErrorCode.BAD_REQUEST,
            "이미 참여가 시작된 퀴즈셋은 문항을 추가하거나 지울 수 없습니다(현재 ${existingQuizzes.size}개). " +
                "문구 수정과 순서 변경만 가능합니다.",
        )
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

    private fun saveChoices(
        quizId: Long,
        choiceForms: List<QuizChoiceForm>,
        existingChoices: List<QuizChoice>,
    ) {
        val submittedChoiceIds = choiceForms.mapNotNull { it.id }.toSet()
        val removedChoiceIds = existingChoices.map { it.id }.filterNot { it in submittedChoiceIds }
        if (removedChoiceIds.isNotEmpty()) quizChoiceRepository.deleteAllByIdInBatch(removedChoiceIds)

        val existingChoiceById = existingChoices.associateBy { it.id }
        choiceForms.forEachIndexed { index, choiceForm ->
            val displayOrder = index + 1
            if (choiceForm.id == null) {
                quizChoiceRepository.save(
                    QuizChoice.create(quizId = quizId, content = choiceForm.content, displayOrder = displayOrder),
                )
                return@forEachIndexed
            }

            val choice =
                existingChoiceById[choiceForm.id] ?: throw WarnException(ErrorCode.NOT_FOUND, "선택지를 찾을 수 없습니다.")
            choice.update(choiceForm.content, displayOrder)
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
