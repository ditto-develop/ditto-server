package com.ditto.api.match.matching

import kotlin.random.Random

/**
 * 회원마다 점수 높은 순으로 picksPerMember 개를 고르고, 둘 중 한 명이라도 고른 매칭은 남긴다.
 * 양쪽 다 골라야 남기면 남들과 두루 점수가 낮은 회원은 후보가 0명이 된다.
 * 대신 여러 사람에게 뽑힌 회원은 picksPerMember 개보다 많이 받는다.
 */
object TopPicksSelector {

    fun select(
        matches: List<ScoredMatch>,
        picksPerMember: Int,
        random: Random = Random.Default,
    ): List<ScoredMatch> {
        if (matches.isEmpty()) return matches

        val matchesByMemberId = buildMap<Long, MutableList<ScoredMatch>> {
            matches.forEach { match ->
                match.memberIds.forEach { memberId -> getOrPut(memberId) { mutableListOf() }.add(match) }
            }
        }

        val pickedMatches = matchesByMemberId.values.flatMapTo(mutableSetOf()) { memberMatches ->
            // 동점은 무작위로 자른다. ID 순으로 자르면 특정 회원이 늘 유리하다.
            // comparator 안에서 random 을 부르면 비교 결과가 어긋나서 먼저 섞고 stable 정렬한다.
            memberMatches
                .shuffled(random)
                .sortedByDescending { it.score }
                .take(picksPerMember)
        }

        return matches.filter { it in pickedMatches }
    }
}
