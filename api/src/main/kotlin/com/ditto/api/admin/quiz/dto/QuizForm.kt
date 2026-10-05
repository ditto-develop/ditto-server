package com.ditto.api.admin.quiz.dto

/**
 * 문항 편집 폼 한 행. displayOrder 는 받지 않고 제출 순서로 서버가 매긴다.
 * id 가 있으면 기존 행 수정, 없으면 신규.
 */
class QuizForm(
    var id: Long? = null,
    var question: String = "",
    var choices: MutableList<QuizChoiceForm> = mutableListOf(),
) {
    /** 저장할 수 없는 첫 이유. 앞에 "N번 문항의 "를 붙여 읽는 문장이다. 문제가 없으면 null. */
    fun invalidReason(): String? = when {
        question.isBlank() -> "질문 항목이 비어 있습니다."
        choices.size < NEW_QUIZ_CHOICE_COUNT -> "선택지는 ${NEW_QUIZ_CHOICE_COUNT}개가 있어야 합니다."
        choices.any { it.content.isBlank() } -> "선택지 항목이 비어 있습니다."
        question.length > QUESTION_MAX_LENGTH -> "질문이 ${QUESTION_MAX_LENGTH}자를 넘습니다."
        choices.any { it.content.length > CHOICE_MAX_LENGTH } -> "선택지가 ${CHOICE_MAX_LENGTH}자를 넘습니다."
        else -> null
    }

    companion object {
        const val NEW_QUIZ_CHOICE_COUNT = 2

        // quiz.question / quiz_choice.content 컬럼 길이. 넘기면 저장 시점에 DataIntegrityViolation 이 난다.
        private const val QUESTION_MAX_LENGTH = 500
        private const val CHOICE_MAX_LENGTH = 200

        fun blank() = QuizForm(choices = MutableList(NEW_QUIZ_CHOICE_COUNT) { QuizChoiceForm() })
    }
}

class QuizChoiceForm(
    var id: Long? = null,
    var content: String = "",
)
