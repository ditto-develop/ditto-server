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
    /** 채워지지 않은 첫 항목의 이름. 다 채워졌으면 null. */
    fun blankFieldName(): String? = when {
        question.isBlank() -> "질문"
        choices.size < NEW_QUIZ_CHOICE_COUNT -> "선택지"
        choices.any { it.content.isBlank() } -> "선택지"
        question.length > QUESTION_MAX_LENGTH -> "질문(${QUESTION_MAX_LENGTH}자 초과)"
        choices.any { it.content.length > CHOICE_MAX_LENGTH } -> "선택지(${CHOICE_MAX_LENGTH}자 초과)"
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
