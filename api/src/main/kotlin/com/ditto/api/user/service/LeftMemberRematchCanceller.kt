package com.ditto.api.user.service

import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 탈퇴 회원의 미성사 재매칭 쌍을 취소한다 — 탈퇴 트랙과 재매칭 트랙을 잇는 어댑터.
 *
 * 취소하지 않으면 남은 한쪽의 제출로 쌍이 `MATCHED`가 되고, 방 예약이 탈퇴자와의 채팅방을 만든다.
 * 이미 성사된 쌍은 여기서 다루지 않는다 — 탈퇴 자체를 막는다([LeaveProgressChecker]).
 *
 * **탈퇴와 같은 트랜잭션이어야 한다.** 방 예약은 실패해도 다음 스케줄러 주기가 복구하지만, 이 취소가
 * 따로 실패하면 탈퇴는 확정된 채 쌍이 남아 상대의 나중 제출로 방이 열린다 — 복구할 주체가 없다.
 * 배경은 `docs/domains/rematch.md`.
 */
@Component
class LeftMemberRematchCanceller(
    private val rematchRepository: RematchRepository,
) {
    /** 그 사이 상대가 성사시킨 쌍은 건드리지 않는다 — 잠근 뒤 다시 판정하기 때문이다(ADR 0011). */
    fun cancelWaitingPairs(memberId: Long) {
        val candidateIds = rematchRepository.findAllIdsByStatusAndMemberId(RematchStatus.WAITING, memberId)
        if (candidateIds.isEmpty()) {
            return
        }

        var cancelled = 0
        candidateIds.forEach { id ->
            val rematch = rematchRepository.findWithLockById(id) ?: return@forEach
            if (rematch.cancelForMemberLeave()) {
                cancelled++
            }
        }
        logger.info { "탈퇴로 재매칭 쌍 취소: memberId=$memberId, 대상 ${candidateIds.size}쌍 중 ${cancelled}쌍" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
