package com.ditto.api.match.matching

import java.util.PriorityQueue
import kotlin.random.Random

/**
 * 회원마다 점수 높은 순으로 picksPerMember 개를 고르고, 둘 중 한 명이라도 고른 매칭은 남긴다.
 * 양쪽 다 골라야 남기면 남들과 두루 점수가 낮은 회원은 후보가 0명이 된다.
 * 대신 여러 사람에게 뽑힌 회원은 picksPerMember 개보다 많이 받는다.
 *
 * 매칭 수가 인원의 제곱에 비례해서 다 모으면 힙이 모자란다. 하나씩 받으며 회원마다 picksPerMember 개만 남긴다.
 */
object TopPicksSelector {

    fun select(
        matches: Sequence<ScoredMatch>,
        picksPerMember: Int,
        random: Random = Random.Default,
    ): List<ScoredMatch> {
        val topPicksByMemberId = mutableMapOf<Long, PriorityQueue<RankedMatch>>()
        matches.forEachIndexed { arrivalOrder, match ->
            match.memberIds.forEach { memberId ->
                // 동점은 무작위로 자른다. ID 순으로 자르면 특정 회원이 늘 유리하다.
                // 값을 두 회원이 같이 쓰면 같은 매칭을 함께 고르게 돼서 남는 매칭이 줄어든다. 회원마다 따로 뽑는다.
                val rankedMatch = RankedMatch(match, tieBreaker = random.nextLong(), arrivalOrder = arrivalOrder)
                topPicksByMemberId
                    .getOrPut(memberId) { PriorityQueue(RankedMatch.LOWEST_FIRST) }
                    .offerKeepingBest(picksPerMember, rankedMatch)
            }
        }

        return topPicksByMemberId.values
            .flatten()
            .distinctBy { it.arrivalOrder }
            .sortedBy { it.arrivalOrder }
            .map { it.match }
    }

    // 맨 앞이 점수가 가장 낮은 매칭이라, 다 찼으면 그것보다 나을 때만 바꾼다.
    private fun PriorityQueue<RankedMatch>.offerKeepingBest(limit: Int, candidate: RankedMatch) {
        if (size < limit) {
            add(candidate)
            return
        }
        if (RankedMatch.LOWEST_FIRST.compare(candidate, peek()) <= 0) return
        poll()
        add(candidate)
    }

    private class RankedMatch(val match: ScoredMatch, val tieBreaker: Long, val arrivalOrder: Int) {
        companion object {
            val LOWEST_FIRST: Comparator<RankedMatch> = compareBy({ it.match.score }, { it.tieBreaker })
        }
    }
}
