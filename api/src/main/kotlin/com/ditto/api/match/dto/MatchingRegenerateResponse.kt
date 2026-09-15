package com.ditto.api.match.dto

import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.CandidateGenerationSummary
import com.ditto.domain.quiz.entity.MatchingType

/** 어드민 매칭 후보 재생성 결과. 저장하지 않는 값이라 응답에만 실린다. */
data class MatchingRegenerateResponse(
    val quizSetId: Long,
    val matchingType: MatchingType,
    /** 제외 정책을 통과해 후보 풀에 들어간 인원 */
    val participantCount: Int,
    /** 지운 후보 행 수 */
    val deletedRowCount: Int,
    /** 새로 쓴 후보 행 수 */
    val savedRowCount: Int,
    val candidates: List<RegeneratedCandidate>,
) {
    companion object {
        fun from(summary: CandidateGenerationSummary): MatchingRegenerateResponse = MatchingRegenerateResponse(
            quizSetId = summary.quizSetId,
            matchingType = summary.matchingType,
            participantCount = summary.participantCount,
            deletedRowCount = summary.rowCounts.deletedCount,
            savedRowCount = summary.rowCounts.savedCount,
            candidates = summary.matches.map { RegeneratedCandidate.from(it) },
        )
    }
}

/** 매칭 한 건. 1:1은 회원 2명, 그룹은 3~6명. */
data class RegeneratedCandidate(
    val memberIds: List<Long>,
    val score: Double,
    /** 같은 답을 고른 문항 수. 1:1만 값이 있다 */
    val matchedQuestionCount: Int?,
    /** 비교한 전체 문항 수. 1:1만 값이 있다 */
    val totalQuestionCount: Int?,
) {
    companion object {
        fun from(match: ScoredMatch): RegeneratedCandidate = RegeneratedCandidate(
            memberIds = match.memberIds.sorted(),
            score = match.score,
            matchedQuestionCount = match.matchedQuestionCount,
            totalQuestionCount = match.totalQuestionCount,
        )
    }
}
