package com.ditto.api.system

import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
            ?.resolve(realNow())
            ?: realNow()

    /**
     * DB 컬럼이 `DATETIME(6)`이라 마이크로초까지만 저장된다. 절삭하지 않으면 리눅스처럼
     * 나노초를 주는 환경에서 **메모리의 값과 저장된 값이 달라져**, 방금 쓴 시각을 그대로 응답하는 요청과
     * 다시 읽어 응답하는 요청이 서로 다른 값을 내보낸다(멱등 응답이 어긋난다).
     */
    private fun realNow(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
}
