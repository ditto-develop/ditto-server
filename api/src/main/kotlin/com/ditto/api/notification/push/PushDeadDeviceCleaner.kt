package com.ditto.api.notification.push

import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.infrastructure.fcm.PushSender
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 발송 결과가 무효(`UNREGISTERED` — 앱 삭제 등)로 판정한 토큰을 주소록에서 지운다.
 * [PushSender]의 콜백(FCM 응답 스레드)에서 불리므로, 트랜잭션을 여기서 새로 연다.
 */
@Component
class PushDeadDeviceCleaner(
    private val memberDeviceRepository: MemberDeviceRepository,
) {

    @Transactional
    fun clean(deadTokens: List<String>) {
        val deletedCount = memberDeviceRepository.deleteAllByTokenIn(deadTokens)
        logger.info { "죽은 디바이스 토큰 정리: ${deletedCount}건" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
