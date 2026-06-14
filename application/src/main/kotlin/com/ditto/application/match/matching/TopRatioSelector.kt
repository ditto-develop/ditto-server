package com.ditto.application.match.matching

import kotlin.math.ceil

/**
 * 상위 [topRatio] + 동점 포함 선발.
 *
 * 기준 개수 = ⌈total × topRatio⌉ 로 컷오프를 정하되, 커트라인 점수와 같은 점수의 페어는
 * 모두 포함한다. 따라서 실제 선발 수는 비율을 초과할 수 있다.
 */
object TopRatioSelector {

    fun select(scoredDuos: List<ScoredDuo>, topRatio: Double): List<ScoredDuo> {
        if (scoredDuos.isEmpty()) return emptyList()

        val duosSortedByScore = scoredDuos.sortedByDescending { it.score }
        val cutoffScore = calculateCutoffScore(duosSortedByScore, topRatio)

        return duosSortedByScore.filter { it.score >= cutoffScore }
    }

    private fun calculateCutoffScore(
        duosSortedByScore: List<ScoredDuo>,
        topRatio: Double,
    ): Double {
        val cutoffCount = ceil(duosSortedByScore.size * topRatio).toInt().coerceIn(1, duosSortedByScore.size)
        val cutoffScore = duosSortedByScore[cutoffCount - 1].score
        return cutoffScore
    }
}
