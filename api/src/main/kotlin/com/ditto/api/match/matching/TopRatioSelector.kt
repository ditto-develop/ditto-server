package com.ditto.api.match.matching

import kotlin.math.ceil

/**
 * 상위 [topRatio] + 동점 포함 선발.
 *
 * 기준 개수 = ⌈total × topRatio⌉ 로 컷오프를 정하되, 커트라인 점수와 같은 점수는 모두 포함한다.
 * 따라서 실제 선발 수는 비율을 초과할 수 있다.
 */
object TopRatioSelector {

    fun select(matches: List<ScoredMatch>, topRatio: Double): List<ScoredMatch> {
        if (matches.isEmpty()) return emptyList()

        val matchesSortedByScore = matches.sortedByDescending { it.score }
        val cutoffScore = calculateCutoffScore(matchesSortedByScore, topRatio)

        return matchesSortedByScore.filter { it.score >= cutoffScore }
    }

    private fun calculateCutoffScore(
        matchesSortedByScore: List<ScoredMatch>,
        topRatio: Double,
    ): Double {
        val cutoffCount = ceil(matchesSortedByScore.size * topRatio).toInt()
            .coerceIn(1, matchesSortedByScore.size)
        return matchesSortedByScore[cutoffCount - 1].score
    }
}
