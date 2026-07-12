package com.ditto.api.sanction.service

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 정지 만료 원복. 인증 필터는 만료를 읽기 전용으로 판정만 하므로(ADR 0009),
 * `Member.status` 원복과 sanction의 EXPIRED 전이는 매칭 배치 시작부와 로그인 시점이 수행한다.
 */
@Service
class SanctionExpiryService(
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
) {

    /** 해제 예정일이 지난 정지 회원 전체를 원복하고, 기한이 지난 제재를 일괄 종결한다 (배치 진입점, 멱등). */
    @Transactional
    fun expireDue(now: LocalDateTime) {
        memberRepository.findAllByStatusAndSuspendedUntilLessThanEqual(MemberStatus.SUSPENDED, now)
            .forEach { it.reinstate() }
        sanctionRepository.findAllByStatusAndEndsAtLessThanEqual(SanctionStatus.ACTIVE, now)
            .forEach { it.expire() }
    }

    /** 로그인한 회원의 만료 지난 정지를 개별 원복한다 (해당 회원의 기한 지난 제재도 함께 종결). */
    @Transactional
    fun reinstateIfExpired(member: Member, now: LocalDateTime) {
        if (member.status != MemberStatus.SUSPENDED || member.isSuspendedAt(now)) {
            return
        }
        member.reinstate()
        memberRepository.save(member)
        sanctionRepository.findAllByMemberIdAndStatus(member.id, SanctionStatus.ACTIVE)
            .filter { sanction -> sanction.endsAt?.isAfter(now) == false }
            .forEach { it.expire() }
    }
}
