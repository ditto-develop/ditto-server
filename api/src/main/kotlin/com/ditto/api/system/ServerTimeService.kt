package com.ditto.api.system

import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 서버 시각 오버라이드 설정/해제(어드민용). 단일 행을 생성·갱신한다.
 */
@Service
@Transactional
class ServerTimeService(
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
) {
    @Transactional(readOnly = true)
    fun getOverride(): ServerTimeOverride =
        serverTimeOverrideRepository.findFirstByOrderByIdAsc() ?: ServerTimeOverride.disabled()

    /** 서버 시각을 [dateTime] 으로 오버라이드한다. */
    fun override(dateTime: LocalDateTime, authorName: String?, authorEmail: String?) {
        val current = serverTimeOverrideRepository.findFirstByOrderByIdAsc() ?: ServerTimeOverride.disabled()
        current.override(dateTime, authorName, authorEmail)
        serverTimeOverrideRepository.save(current)
    }

    /** 오버라이드를 해제한다(이후 실제 시각 사용). */
    fun disable() {
        val current = serverTimeOverrideRepository.findFirstByOrderByIdAsc() ?: return
        current.disable()
        serverTimeOverrideRepository.save(current)
    }
}
