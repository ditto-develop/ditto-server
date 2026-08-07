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
 * 예약 조회에서 걸러내는 대안을 버린 배경은 `docs/domains/rematch.md`.
 */
@Component
class LeftMemberRematchCanceller(
    private val rematchRepository: RematchRepository,
) {
    /**
     * 대상을 찾은 뒤 **행을 잠그고 다시 판정한다.** 잠그지 않으면 그 사이 상대가 제출해 성사시킨 커밋을
     * 낡은 스냅샷이 덮어(전 컬럼 UPDATE) 통보된 성사가 조용히 취소된다 — 제출 경로가 행을 잠그는 것과
     * 같은 이유다(ADR 0011).
     *
     * `findWithLockById`가 `Propagation.MANDATORY`라 트랜잭션 없이 부르면 예외로 드러난다.
     */
    fun cancelWaitingPairs(memberId: Long) {
        val candidateIds = rematchRepository
            .findAllByStatusAndMemberId(RematchStatus.WAITING, memberId)
            .map { it.id }
        if (candidateIds.isEmpty()) return

        val cancelled = candidateIds.count { id ->
            rematchRepository.findWithLockById(id)?.cancelForMemberLeave() == true
        }
        logger.info { "탈퇴로 재매칭 쌍 취소: memberId=$memberId, 대상 ${candidateIds.size}쌍 중 ${cancelled}쌍" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
