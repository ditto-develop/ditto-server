package com.ditto.api.match.service

import com.ditto.domain.match.entity.CandidateOrigin

/** 한 셋에 시크릿 매칭 라운드를 돌린 결과. 같은 라운드가 이미 돌았으면 [alreadyGenerated]다. */
data class SecretRoundResult(
    val quizSetId: Long,
    val round: CandidateOrigin,
    val alreadyGenerated: Boolean,
    val poolSize: Int,
    val savedCount: Int,
    val newCandidateOwnerIds: Set<Long>,
) {
    companion object {
        fun alreadyGenerated(quizSetId: Long, round: CandidateOrigin) = SecretRoundResult(
            quizSetId = quizSetId,
            round = round,
            alreadyGenerated = true,
            poolSize = 0,
            savedCount = 0,
            newCandidateOwnerIds = emptySet(),
        )
    }
}
