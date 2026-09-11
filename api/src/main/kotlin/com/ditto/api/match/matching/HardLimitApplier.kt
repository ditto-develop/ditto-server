package com.ditto.api.match.matching

import kotlin.random.Random

/**
 * 1인 [hardLimit] 제한 (구성원 전원 생존).
 *
 * 회원별로 무작위로 섞은 뒤 점수 desc 로 정렬해 상위 [hardLimit] 만 "유지 집합"으로 둔다.
 * 정렬이 stable 하므로 점수가 다르면 결과는 결정적이고, **동점일 때만** 무작위 순서가 된다.
 * (ID 같은 결정적 키로 깨면 특정 회원이 체계적으로 유리해지므로 무작위로 처리)
 *
 * 매칭은 구성원 **전원**의 유지 집합에 들어야 살아남는다. 한 명에게서라도 밀려나면 나머지
 * 구성원의 노출 목록에서도 통째로 사라진다. 1:1에서는 이것이 양방향 원칙이고, 그룹에서는
 * "제외된 그룹은 다른 멤버에게도 표시되지 않는다"와 같은 규칙이다.
 */
object HardLimitApplier {

    fun apply(
        matches: List<ScoredMatch>,
        hardLimit: Int,
        random: Random = Random.Default,
    ): List<ScoredMatch> {
        if (matches.isEmpty()) return matches

        val matchesByMemberId = buildMap<Long, MutableList<ScoredMatch>> {
            matches.forEach { match ->
                match.memberIds.forEach { memberId -> getOrPut(memberId) { mutableListOf() }.add(match) }
            }
        }

        val selectedMatchesByMemberId = matchesByMemberId.mapValues { (_, memberMatches) ->
            // 미리 섞은 뒤 stable 정렬 → 점수는 결정적, 동점만 무작위.
            // (random 을 comparator 안에서 직접 호출하면 비교 일관성이 깨지므로 shuffle 로 처리)
            memberMatches
                .shuffled(random)
                .sortedByDescending { it.score }
                .take(hardLimit)
                .toSet()
        }

        return matches.filter { match -> isSelectedByAllMembers(match, selectedMatchesByMemberId) }
    }

    /** 구성원 전원의 선발 집합에 들어야 살아남는다. 한 명이라도 빠뜨리면 제거된다. */
    private fun isSelectedByAllMembers(
        match: ScoredMatch,
        selectedMatchesByMemberId: Map<Long, Set<ScoredMatch>>,
    ): Boolean =
        match.memberIds.all { memberId -> selectedMatchesByMemberId.getValue(memberId).contains(match) }
}
