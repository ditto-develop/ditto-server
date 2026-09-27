package com.ditto.api.user.facade

import com.ditto.api.user.dto.NicknameReservationResponse
import com.ditto.api.user.service.NicknameService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.NicknamePolicy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * 닉네임 확인 + 10분 예약(v2) 진입점.
 *
 * 트랜잭션을 열지 않는다 — 두 회원이 같은 닉네임을 동시에 처음 예약하면 진 쪽 트랜잭션이 유일 제약으로
 * rollback-only 가 되므로, 그 예외를 트랜잭션 밖에서 잡아야 "사용 불가"라는 정상 응답으로 바꿀 수 있다.
 */
@Component
class NicknameReservationFacade(
    private val nicknameService: NicknameService,
) {

    /** 형식이 틀린 닉네임은 예약하지 않는다 — 어차피 저장할 수 없는 값으로 남의 선택을 막지 않게. */
    fun checkAndReserve(memberId: Long, nickname: String): NicknameReservationResponse {
        if (nickname.length !in NicknamePolicy.MIN_LENGTH..NicknamePolicy.MAX_LENGTH || !NICKNAME_REGEX.matches(nickname)) {
            throw WarnException(ErrorCode.BAD_REQUEST, NicknamePolicy.INVALID_MESSAGE)
        }
        val expiresAt = runCatching { nicknameService.reserve(memberId, nickname) }
            .getOrElse { failure ->
                if (failure !is DataIntegrityViolationException) throw failure
                null
            }
        return NicknameReservationResponse(available = expiresAt != null, reservedUntil = expiresAt)
    }

    companion object {
        private val NICKNAME_REGEX = Regex(NicknamePolicy.PATTERN)
    }
}
