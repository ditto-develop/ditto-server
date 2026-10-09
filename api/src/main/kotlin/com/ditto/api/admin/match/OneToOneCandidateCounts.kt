package com.ditto.api.admin.match

import com.ditto.api.match.service.CandidateGenerationSummary
import com.ditto.domain.quiz.entity.MatchingType

// 1:1 후보 수는 5명이 상한이 아니라서 재생성 결과에 회원별 분포를 같이 보여 준다.
class OneToOneCandidateCounts(
    val memberCountWithoutCandidate: Int,
    val minCandidateCount: Int,
    val maxCandidateCount: Int,
) {
    companion object {
        fun of(summary: CandidateGenerationSummary): OneToOneCandidateCounts? {
            if (summary.matchingType != MatchingType.ONE_TO_ONE) return null

            val candidateCountByMemberId = summary.matches.flatMap { it.memberIds }.groupingBy { it }.eachCount()
            return OneToOneCandidateCounts(
                memberCountWithoutCandidate = summary.participantCount - candidateCountByMemberId.size,
                minCandidateCount = candidateCountByMemberId.values.minOrNull() ?: 0,
                maxCandidateCount = candidateCountByMemberId.values.maxOrNull() ?: 0,
            )
        }
    }
}
