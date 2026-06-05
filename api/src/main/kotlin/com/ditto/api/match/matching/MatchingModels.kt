package com.ditto.api.match.matching

/**
 * 매칭 알고리즘 입력: 한 참여자와 그 답변.
 *
 * @property answers quizId -> choiceId (같은 quizId에 같은 choiceId 면 답변 일치)
 */
data class MatchParticipant(
    val memberId: Long,
    val answers: Map<Long, Long>,
)

/**
 * 매칭 점수 계산 결과. 점수와 그 근거(일치/전체 문항 수)를 함께 담는다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0, 소수점 1자리)
 * @property matchedQuestionCount 같은 답을 고른 문항 수
 * @property totalQuestionCount 비교한 전체 문항 수
 */
data class MatchScore(
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
)

/**
 * 점수화된 페어. memberId1 < memberId2 로 정규화하여 방향 무관하게 동일 페어를 표현한다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0, 소수점 1자리)
 * @property matchedQuestionCount 같은 답을 고른 문항 수 (scoreBreakdown 노출용)
 * @property totalQuestionCount 비교한 전체 문항 수
 */
data class ScoredDuo(
    val memberId1: Long,
    val memberId2: Long,
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
) {
    companion object {
        fun of(
            memberA: Long,
            memberB: Long,
            score: Double,
            matchedQuestionCount: Int,
            totalQuestionCount: Int,
        ): ScoredDuo =
            ScoredDuo(
                memberId1 = minOf(memberA, memberB),
                memberId2 = maxOf(memberA, memberB),
                score = score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
            )
    }
}
