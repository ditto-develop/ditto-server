package com.ditto.api.system

import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 서버의 "현재 시각" 제공자.
 *
 * 어드민이 설정한 오버라이드가 활성이면 그 시각을, 아니면 실제 시각([LocalDateTime.now])을 반환한다.
 * 시각에 의존하는 온디맨드 조회(예: 이번 주 퀴즈셋, 시스템 상태)는 모두 이 값을 사용한다.
 * (실시간 스케줄러는 의도적으로 실제 시각을 사용한다 — 본 제공자를 쓰지 않는다.)
 */
@Component
class ServerTimeProvider(
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
) {
    @Transactional(readOnly = true)
    fun now(): LocalDateTime =
        serverTimeOverrideRepository.findFirstByOrderByIdAsc()
            ?.resolve(LocalDateTime.now())
            ?: LocalDateTime.now()
}
