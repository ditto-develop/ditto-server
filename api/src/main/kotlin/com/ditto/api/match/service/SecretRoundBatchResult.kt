package com.ditto.api.match.service

/**
 * 시크릿 매칭 배치 한 번의 결과. 라운드 전에 목요일 정규 배치가 놓친 셋을 메웠으면 [regularQuizSetIds]에 담겨,
 * 정규 매칭 결과 알림을 보낼 수 있다.
 */
data class SecretRoundBatchResult(
    val regularQuizSetIds: List<Long>,
    val roundResults: List<SecretRoundResult>,
) {
    companion object {
        val NOTHING_DUE = SecretRoundBatchResult(emptyList(), emptyList())
    }
}
