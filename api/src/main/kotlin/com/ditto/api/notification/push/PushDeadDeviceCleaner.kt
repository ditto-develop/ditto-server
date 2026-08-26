package com.ditto.api.notification.push

import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.infrastructure.fcm.PushSender
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 무효 판정(`UNREGISTERED`)된 토큰을 주소록에서 지운다.
 * FCM 응답 스레드의 콜백에서 불리므로 트랜잭션을 여기서 새로 연다.
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
