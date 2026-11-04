package com.ditto.api.chat.quiz

import com.ditto.api.quiz.stat.QuizAnswerSheet

/**
 * 그룹 방 한 문항의 답 분포. 분포 화면(CH-03·04)과 첫 대화 힌트가 같은 규칙을 쓴다.
 *
 * @param countsByChoiceId 선택지별 인원(선택지 순서). 답하지 않은 멤버는 어느 칸에도 없다.
 */
class GroupAnswerSpread(private val countsByChoiceId: Map<Long, Int>) {

    /** 답한 사람이 모두 같은 답을 골랐으면 그 선택지. 아무도 답하지 않았거나 갈렸으면 null. */
    val unanimousChoiceId: Long? = countsByChoiceId.filterValues { it > 0 }.keys.singleOrNull()

    val unanimous: Boolean
        get() = unanimousChoiceId != null

    /** 가장 많이 고른 칸과 가장 적게 고른 칸의 인원 차. '반반 갈린 질문'은 이 값이 작은 순이다. */
    val gap: Int
        get() = (countsByChoiceId.values.maxOrNull() ?: 0) - (countsByChoiceId.values.minOrNull() ?: 0)

    fun countOf(choiceId: Long): Int = countsByChoiceId[choiceId] ?: 0

    companion object {
        fun of(sheet: QuizAnswerSheet, quizId: Long, memberIds: Collection<Long>): GroupAnswerSpread =
            GroupAnswerSpread(sheet.countsAmong(quizId, memberIds))
    }
}
