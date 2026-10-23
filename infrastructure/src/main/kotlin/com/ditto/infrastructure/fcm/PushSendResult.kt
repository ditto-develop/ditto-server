package com.ditto.infrastructure.fcm

/**
 * FCM 응답 한 번의 결과. 실패한 기기마다 실패 코드가 하나씩 들어 있고 성공한 기기는 없다.
 * 무효 토큰(`UNREGISTERED`, 앱 삭제 등)은 호출부가 주소록에서 지워야 해서 따로 담는다.
 */
data class PushSendResult(
    val failureCodes: List<String>,
    val deadTokens: List<String>,
)
