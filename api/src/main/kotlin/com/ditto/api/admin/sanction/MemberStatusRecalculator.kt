package com.ditto.api.admin.sanction

import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/** 제재를 해제하거나 지운 같은 트랜잭션에서 부른다. sanction 이 SSOT 이고 Member.status 는 반영값이다(ADR 0009 제재 SSOT). */
@Component
class MemberStatusRecalculator(
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
) {
    fun recalculateFromRemainingSanctions(memberId: Long, now: LocalDateTime) {
        val member = memberRepository.findWithLockById(memberId) ?: return
        val remainingSanctions = sanctionRepository.findAllWithLockByMemberIdAndStatus(memberId, SanctionStatus.ACTIVE)
        member.alignStatusWith(remainingSanctions, now)
    }
}
