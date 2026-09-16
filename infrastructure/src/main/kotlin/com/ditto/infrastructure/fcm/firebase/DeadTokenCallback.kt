package com.ditto.infrastructure.fcm.firebase

import com.google.api.core.ApiFutureCallback
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.MessagingErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 발송 결과에서 무효 토큰(`UNREGISTERED` — 앱 삭제 등)만 골라 [onDeadTokens]로 돌려준다.
 * 응답 순서는 요청 [tokens] 순서와 같다. 그 외 실패는 일시적일 수 있어 로그만 남긴다.
 *
 * 그 로그에 에러코드를 함께 적는다. `SENDER_ID_MISMATCH`(다른 Firebase 프로젝트에서 발급된 토큰)처럼
 * 재시도해도 낫지 않는 실패와 `UNAVAILABLE` 같은 일시 장애는 코드 없이 가를 수 없고,
 * 가르지 못하면 주소록에서 지울지 기다릴지도 정하지 못한다.
 *
 * 토큰은 뒤 [TOKEN_LOG_SUFFIX_LENGTH]자만 남긴다 — 토큰 전체는 소유 증명 없이 푸시 수신처를 넘기는
 * 비밀값이라 로그에 실으면 안 된다(`MemberDeviceRegisterRequest`).
 */
internal class DeadTokenCallback(
    private val tokens: List<String>,
    private val notificationType: String?,
    private val onDeadTokens: (List<String>) -> Unit,
) : ApiFutureCallback<BatchResponse> {

    override fun onSuccess(result: BatchResponse) {
        val (unregistered, others) = tokens.zip(result.responses)
            .mapNotNull { (token, response) -> response.exception?.let { token to it.messagingErrorCode } }
            .partition { (_, errorCode) -> errorCode == MessagingErrorCode.UNREGISTERED }

        if (others.isNotEmpty()) {
            logger.warn { "푸시 일부 실패: type=${notificationType ?: "-"}, ${others.joinToString(transform = ::describe)}" }
        }

        val deadTokens = unregistered.map { (token, _) -> token }
        if (deadTokens.isNotEmpty()) {
            onDeadTokens(deadTokens)
        }
    }

    override fun onFailure(t: Throwable) {
        logger.warn(t) { "푸시 발송 실패 — 무시한다: tokens=${tokens.size}개" }
    }

    private fun describe(failure: Pair<String, MessagingErrorCode?>): String {
        val (token, errorCode) = failure
        return "$errorCode(…${token.takeLast(TOKEN_LOG_SUFFIX_LENGTH)})"
    }

    companion object {
        /** 같은 회원의 기기를 구분할 정도만. 늘리면 토큰 자체가 로그에 남는 쪽으로 기운다. */
        private const val TOKEN_LOG_SUFFIX_LENGTH = 8

        private val logger = KotlinLogging.logger {}
    }
}
