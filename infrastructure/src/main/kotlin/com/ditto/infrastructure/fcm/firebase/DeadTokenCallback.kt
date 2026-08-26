package com.ditto.infrastructure.fcm.firebase

import com.google.api.core.ApiFutureCallback
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.MessagingErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 발송 결과에서 무효 토큰(`UNREGISTERED` — 앱 삭제 등)만 골라 [onDeadTokens]로 돌려준다.
 * 응답 순서는 요청 [tokens] 순서와 같다. 그 외 실패는 일시적일 수 있어 로그만 남긴다.
 */
internal class DeadTokenCallback(
    private val tokens: List<String>,
    private val onDeadTokens: (List<String>) -> Unit,
) : ApiFutureCallback<BatchResponse> {

    override fun onSuccess(result: BatchResponse) {
        val deadTokens = result.responses
            .withIndex()
            .filter { (_, response) -> response.exception?.messagingErrorCode == MessagingErrorCode.UNREGISTERED }
            .map { (index, _) -> tokens[index] }
        if (result.failureCount > deadTokens.size) {
            logger.warn { "푸시 일부 실패: 실패 ${result.failureCount}건 중 무효 토큰 ${deadTokens.size}건" }
        }
        if (deadTokens.isNotEmpty()) {
            onDeadTokens(deadTokens)
        }
    }

    override fun onFailure(t: Throwable) {
        logger.warn(t) { "푸시 발송 실패 — 무시한다: tokens=${tokens.size}개" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
