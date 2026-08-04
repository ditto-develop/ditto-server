package com.ditto.api.rematch.service

import com.ditto.api.chat.service.ChatService
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 성사된 재매칭에 채팅방을 예약한다 — 재매칭 트랙과 채팅 트랙을 잇는 어댑터.
 *
 * **성사 트랜잭션은 방을 만들지 않는다.** 성사는 평가 제출 중에 확정되므로(`RematchSubmitter`), 방 생성을
 * 그 트랜잭션에 묶으면 방 생성 실패가 평가 제출을 되돌린다. 대신 "성사됐는데 방이 없다"를 정상 상태로
 * 두고 이 어댑터가 맞춘다. 방이 곧 처리 완료 기록이라 별도 표시나 이벤트 전달 장치가 필요 없다.
 *
 * **방을 만드는 주체는 이것 하나여야 한다.** 성사 직후 즉시 만드는 경로를 더하면 writer 가 둘이 되어,
 * 같은 쌍의 방이 둘 열리는 것을 잠금 없이 막을 수 없다(ADR 0013). 재매칭 방은 금요일에 열리므로
 * 즉시 만들 이유도 없다.
 */
@Component
class RematchChatRoomOpener(
    private val rematchRepository: RematchRepository,
    private val chatService: ChatService,
) {
    /**
     * 방이 없는 성사분에 방을 예약한다. 스케줄러가 주기적으로 부른다.
     *
     * 쌍마다 격리한다 — `createRematchRoom`이 다른 빈이라 호출마다 자기 트랜잭션을 얻으므로 한 쌍이
     * 실패해도 그 쌍만 롤백된다. 배치 전체를 한 트랜잭션으로 묶으면 뒤쪽 한 건 때문에 앞서 성공한
     * 것까지 폐기되고, 조회가 그 쌍을 매 주기 다시 집어오므로 한 쌍이 예약을 영구히 막는다.
     *
     * @return 이번 호출로 새로 만들어진 방 수
     */
    fun openMissing(): Int {
        val matched = rematchRepository.findMatchedWithoutChatRoom(RESERVE_BATCH_SIZE)
        if (matched.isEmpty()) {
            return 0
        }

        val opened = matched.count { rematch ->
            runCatchingExceptions { reserve(rematch) }
                .onFailure { logger.warn(it) { "재매칭 방 예약 실패 — 다음 주기로 넘긴다: rematchId=${rematch.id}" } }
                .isSuccess
        }
        logger.info { "재매칭 방 예약: 대상 ${matched.size}건 중 ${opened}건 생성" }
        return opened
    }

    private fun reserve(rematch: Rematch) {
        val matchedAt = rematch.matchedAt()
            ?: error("성사되지 않은 재매칭에는 방을 만들 수 없습니다: rematchId=${rematch.id}")

        chatService.createRematchRoom(
            rematchId = rematch.id,
            memberIds = listOf(rematch.memberId1, rematch.memberId2),
            matchedAt = matchedAt,
        )
    }

    /**
     * [runCatching]과 같지만 [Error]는 삼키지 않는다 — `OutOfMemoryError` 같은 치명 오류가 WARN 한 줄로
     * 묻히고, 이미 불안정한 JVM 에서 다음 쌍 처리를 계속 시도하게 되는 것을 막는다.
     */
    private inline fun runCatchingExceptions(block: () -> Unit): Result<Unit> =
        runCatching(block).onFailure { if (it !is Exception) throw it }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 한 번의 예약이 떠안을 최대 쌍 수. 장애 후 밀린 물량이 한 호출을 오래 잡지 않게 끊는다. */
        private const val RESERVE_BATCH_SIZE = 100
    }
}
