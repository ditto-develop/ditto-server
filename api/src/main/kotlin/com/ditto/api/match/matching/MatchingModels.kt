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

/** 매칭 알고리즘 입력 컨텍스트 */
data class MatchingContext(
    val participants: List<MatchParticipant>,
)

/**
 * 점수화된 페어. memberId1 < memberId2 로 정규화하여 방향 무관하게 동일 페어를 표현한다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0, 소수점 1자리)
 */
data class ScoredDuo(
    val memberId1: Long,
    val memberId2: Long,
    val score: Double,
) {
    companion object {
        fun of(memberA: Long, memberB: Long, score: Double): ScoredDuo =
            ScoredDuo(
                memberId1 = minOf(memberA, memberB),
                memberId2 = maxOf(memberA, memberB),
                score = score,
            )
    }
}

/** 매칭 알고리즘 결과: 최종 생존한 duo 목록 (각 duo 는 양방향 노출을 의미) */
data class MatchingResult(
    val survivingDuos: List<ScoredDuo>,
)
