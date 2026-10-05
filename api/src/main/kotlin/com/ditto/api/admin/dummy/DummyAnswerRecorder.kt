package com.ditto.api.admin.dummy

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Component

/** 더미의 퀴즈 답과 진행을 실회원 제출과 같은 모양으로 남긴다. */
@Component
class DummyAnswerRecorder(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
) {

    fun findQuestionsOf(quizSetId: Long): QuizQuestions {
        val quizSet = quizSetRepository.findById(quizSetId)
            .orElseThrow { WarnException(ErrorCode.NOT_FOUND, "없는 퀴즈셋입니다: #$quizSetId") }
        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
            .ifEmpty { throw WarnException(ErrorCode.BAD_REQUEST, "문항이 없는 퀴즈셋에는 더미를 생성할 수 없습니다.") }
        val choicesByQuizId = quizChoiceRepository
            .findByQuizIdInOrderByDisplayOrderAsc(quizzes.map { it.id })
            .groupBy { it.quizId }
        val quizWithoutChoice = quizzes.firstOrNull { choicesByQuizId[it.id].isNullOrEmpty() }
        if (quizWithoutChoice != null) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "선택지가 없는 문항이 있어 더미를 생성할 수 없습니다: 문항 ID #${quizWithoutChoice.id}",
            )
        }
        return QuizQuestions(quizSet, quizzes, choicesByQuizId)
    }

    /** [pickedChoices]는 문항 순서대로 앞에서부터의 답이다. 비어 있으면 퀴즈를 시작하지 않은 회원으로 둔다. */
    fun recordAnswers(memberId: Long, questions: QuizQuestions, pickedChoices: List<QuizChoice>) {
        if (pickedChoices.isEmpty()) return
        quizAnswerRepository.saveAll(pickedChoices.map { QuizAnswer.create(memberId, it.quizId, it.id) })
        quizProgressRepository.save(progressOf(memberId, questions, pickedChoices.size))
    }

    // status·answeredCount 는 protected set 이라 recordAnswer 를 푼 문항 수만큼 호출해 상태를 맞춘다.
    private fun progressOf(memberId: Long, questions: QuizQuestions, answeredCount: Int): QuizProgress =
        QuizProgress.create(memberId, questions.quizSetId, questions.quizzes.size)
            .apply { repeat(answeredCount) { recordAnswer() } }
}
