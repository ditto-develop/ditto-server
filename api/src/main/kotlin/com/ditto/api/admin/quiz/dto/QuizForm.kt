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
    /** 화면이 기본으로 띄우는 빈 행. 저장 대상에서 제외한다. */
    fun isEmpty(): Boolean = question.isBlank() && choices.all { it.content.isBlank() }

    fun blankFieldName(): String? = when {
        question.isBlank() -> "질문"
        choices.size < MIN_CHOICE_COUNT -> "선택지"
        choices.any { it.content.isBlank() } -> "선택지"
        else -> null
    }

    companion object {
        const val MIN_CHOICE_COUNT = 2

        fun blank() = QuizForm(choices = MutableList(MIN_CHOICE_COUNT) { QuizChoiceForm() })
    }
}

class QuizChoiceForm(
    var id: Long? = null,
    var content: String = "",
)
