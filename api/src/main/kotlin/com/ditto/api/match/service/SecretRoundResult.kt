package com.ditto.api.match.service

import com.ditto.domain.match.entity.CandidateOrigin

/** 한 셋에 시크릿 매칭 라운드를 돌린 결과. 같은 라운드가 이미 돌았으면 [alreadyGenerated]이고 숫자는 0 이다. */
data class SecretRoundResult(
    val quizSetId: Long,
    val secretRound: CandidateOrigin,
    val alreadyGenerated: Boolean,
    val poolSize: Int,
    val savedCount: Int,
    val newCandidateOwnerIds: Set<Long>,
) {
    companion object {
        fun skipped(quizSetId: Long, secretRound: CandidateOrigin) = SecretRoundResult(
            quizSetId = quizSetId,
            secretRound = secretRound,
            alreadyGenerated = true,
            poolSize = 0,
            savedCount = 0,
            newCandidateOwnerIds = emptySet(),
        )
    }
}
