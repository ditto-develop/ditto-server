package com.ditto.api.match.matching

/**
 * 풀 크기로 그룹 정원을 정하는 정책.
 *
 * 풀이 작으면 크게 묶어 그룹이 성립하게 하고, 크면 작게 묶어 그룹 수를 늘린다.
 * 기획이 범위(4~5명 / 3~4명)로 준 구간은 큰 쪽을 쓴다 — 성사가 [MIN_SIZE]명 이상 수락이라
 * 정원이 클수록 한 명이 빠져도 견딘다.
 */
object GroupSizePolicy {

    /** 성사 최소 인원. 풀이 이보다 작으면 그룹을 만들 수 없다. */
    const val MIN_SIZE = 3

    fun decide(poolSize: Int): Int = when {
        poolSize < SMALL_POOL_SIZE -> minOf(MAX_SIZE, poolSize)
        poolSize < MEDIUM_POOL_SIZE -> MEDIUM_POOL_GROUP_SIZE
        else -> LARGE_POOL_GROUP_SIZE
    }

    private const val MAX_SIZE = 6
    private const val SMALL_POOL_SIZE = 10
    private const val MEDIUM_POOL_SIZE = 30
    private const val MEDIUM_POOL_GROUP_SIZE = 5
    private const val LARGE_POOL_GROUP_SIZE = 4
}
