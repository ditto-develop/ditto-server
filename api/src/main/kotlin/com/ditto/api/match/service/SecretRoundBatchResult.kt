package com.ditto.api.match.service

import com.ditto.domain.match.entity.CandidateOrigin

/**
 * 시크릿 매칭 배치 한 번의 결과. [backfilledRegularQuizSetIds]는 라운드 전에 목요일 정규 배치가 놓쳐 이번에 메운 셋이라
 * 정규 매칭 결과 알림 대상이다. [failedQuizSetIds]는 정규나 라운드가 실패해 이번에 건너뛴 셋이다.
 */
data class SecretRoundBatchResult(
    val status: Status,
    val secretRound: CandidateOrigin?,
    val backfilledRegularQuizSetIds: List<Long>,
    val roundResults: List<SecretRoundResult>,
    val failedQuizSetIds: List<Long>,
) {
    enum class Status { FLAG_OFF, NO_ROUND_DUE, RAN }

    companion object {
        val FLAG_OFF = SecretRoundBatchResult(Status.FLAG_OFF, null, emptyList(), emptyList(), emptyList())
        val NO_ROUND_DUE = SecretRoundBatchResult(Status.NO_ROUND_DUE, null, emptyList(), emptyList(), emptyList())
    }
}
