package com.ditto.api.user.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.NicknameReservation
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.NicknameReservationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 닉네임을 누가 쓸 수 있는가 — 저장된 회원 닉네임과 10분 예약([NicknameReservation])을 함께 본다.
 *
 * 예약 시각은 실제 시각이다. 서버 시각 오버라이드는 매칭 주차를 옮기는 검증 도구라, 그 값으로 예약 만료를 재면
 * 오버라이드를 켜고 끌 때 예약이 한꺼번에 살아나거나 사라진다.
 */
@Service
class NicknameService(
    private val memberRepository: MemberRepository,
    private val nicknameReservationRepository: NicknameReservationRepository,
) {

    /** 비회원 관점(v1) — 누가 저장했거나 아직 유효하게 예약했으면 사용 불가. */
    @Transactional(readOnly = true)
    fun isAvailable(nickname: String): Boolean {
        if (memberRepository.existsByNickname(nickname)) {
            return false
        }
        val reservation = nicknameReservationRepository.findByNickname(nickname) ?: return true
        return !reservation.isHeldByOtherThan(memberId = NO_MEMBER, now = now())
    }

    /**
     * 쓸 수 있으면 [memberId]에게 10분 동안 예약하고 만료 시각을, 아니면 null 을 준다.
     * 내 닉네임과 내 예약은 막지 않는다. 다른 닉네임을 확인하면 이전 예약은 그 닉네임으로 옮겨 간다.
     *
     * 같은 닉네임을 두 회원이 동시에 처음 예약하면 유일 제약이 한쪽을 막는다 — 호출자가 트랜잭션 밖에서 잡는다.
     */
    @Transactional
    fun reserve(memberId: Long, nickname: String): LocalDateTime? {
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            return null
        }
        val now = now()
        val existing = nicknameReservationRepository.findByNickname(nickname)
        if (existing != null && existing.isHeldByOtherThan(memberId, now)) {
            return null
        }

        val mine = nicknameReservationRepository.findByMemberId(memberId)
        if (existing != null && existing.memberId != memberId) {
            // 남이 쥐었다가 만료된 예약이다. 내 예약을 옮기기 전에 치워야 닉네임 유일 제약에 걸리지 않는다.
            nicknameReservationRepository.delete(existing)
            nicknameReservationRepository.flush()
        }
        val reservation = mine?.apply { renew(nickname, now) }
            ?: nicknameReservationRepository.save(NicknameReservation.reserve(memberId, nickname, now))
        return reservation.expiresAt
    }

    /**
     * 가입·프로필 수정에서 [nickname]을 저장해도 되는지 검사한다. 남이 저장했거나 남이 유효하게 예약했으면 거부한다.
     * 내 예약은 통과시킨다 — 예약한 사람이 가입 버튼을 누르는 경로다.
     */
    @Transactional(readOnly = true)
    fun assertAssignable(memberId: Long, nickname: String) {
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }
        val reservation = nicknameReservationRepository.findByNickname(nickname)
        if (reservation != null && reservation.isHeldByOtherThan(memberId, now())) {
            throw WarnException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }
    }

    /** 닉네임을 저장했으니 붙잡아 둘 이유가 없다. 예약 없이 저장한 경우도 있어 없으면 조용히 넘어간다. */
    @Transactional
    fun release(memberId: Long) {
        nicknameReservationRepository.findByMemberId(memberId)?.let { nicknameReservationRepository.delete(it) }
    }

    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

    companion object {
        /** 로그인하지 않은 조회자. 실제 회원 ID 는 1부터라 누구의 예약과도 겹치지 않는다. */
        private const val NO_MEMBER = 0L
    }
}
