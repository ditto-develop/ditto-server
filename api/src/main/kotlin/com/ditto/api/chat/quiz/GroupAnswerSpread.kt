package com.ditto.api.chat.quiz

/**
 * 그룹 방 한 문항의 답 분포. 분포 화면(CH-03·04)과 첫 대화 힌트가 같은 규칙을 쓴다.
 *
 * @param counts 선택지별 인원(선택지 순서). 답하지 않은 멤버는 어느 칸에도 없다.
 */
class GroupAnswerSpread(val counts: List<Int>) {

    /** 답한 사람이 모두 같은 답을 골랐다. 아무도 답하지 않았으면 만장일치가 아니다. */
    val unanimous: Boolean
        get() = counts.count { it > 0 } == 1

    /** 가장 많이 고른 칸과 가장 적게 고른 칸의 인원 차. '반반 갈린 질문'은 이 값이 작은 순이다. */
    val gap: Int
        get() = (counts.maxOrNull() ?: 0) - (counts.minOrNull() ?: 0)

    companion object {
        const val SPLIT_QUIZ_LIMIT = 3
    }
}
