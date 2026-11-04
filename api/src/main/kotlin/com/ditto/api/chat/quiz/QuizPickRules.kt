package com.ditto.api.chat.quiz

import com.ditto.api.quiz.stat.QuizStatSnapshot

/**
 * 문항을 고르는 순서 규칙. 답변 비교의 강조·'반반 갈린 질문'과 첫 대화 힌트가 같은 순서를 써야 화면과 힌트가 어긋나지 않는다.
 * 동률은 언제나 문항 순서다.
 */
object QuizPickRules {

    /**
     * 함께 고른 답의 **전체 비율이 가장 낮은** 문항. 스냅샷이 없으면(마감 전) 비율을 모두 0으로 보고 문항 순서로 고른다.
     * @param candidates (문항 순서, 함께 고른 선택지 ID, 값)
     */
    fun <T> rarest(candidates: List<Candidate<T>>, snapshot: QuizStatSnapshot?): T? =
        candidates.minWithOrNull(compareBy({ snapshot?.percentOf(it.choiceId) ?: 0 }, { it.order }))?.value

    /** 인원 차가 작은 순. */
    fun <T> closestSplits(candidates: List<T>, gapOf: (T) -> Int, orderOf: (T) -> Int): List<T> =
        candidates.sortedWith(compareBy(gapOf, orderOf))

    data class Candidate<T>(val order: Int, val choiceId: Long, val value: T)
}
