package com.ditto.api.admin.sanction

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Component

/**
 * 남은 유효 제재 중 가장 무거운 것으로 회원 상태를 맞춘다 (경고는 상태와 무관).
 * 제재를 해제하거나 지운 같은 트랜잭션에서 부른다. sanction 이 SSOT 이고 Member.status 는 반영값이라서다(ADR 0009).
 */
@Component
class MemberStatusRecalculator(
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
) {
    fun recalculateBySanctions(memberId: Long, now: LocalDateTime) {
        val member = memberRepository.findById(memberId).getOrNull() ?: return
        if (member.status != MemberStatus.SUSPENDED && member.status != MemberStatus.BANNED) {
            return
        }

        val heaviest = sanctionRepository.findAllByMemberIdAndStatus(memberId, SanctionStatus.ACTIVE)
            .filter { it.isEffectiveAt(now) && it.level != SanctionLevel.WARNING }
            .maxByOrNull { it.level }

        member.reinstate()
        when (heaviest?.level) {
            SanctionLevel.SUSPENSION -> member.suspendUntil(requireNotNull(heaviest.endsAt))
            SanctionLevel.PERMANENT_BAN -> member.ban()
            else -> {}
        }
    }
}
