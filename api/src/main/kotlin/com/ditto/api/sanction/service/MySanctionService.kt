package com.ditto.api.sanction.service

import com.ditto.api.sanction.dto.ActiveSanctionResponse
import com.ditto.api.sanction.dto.MySanctionResponse
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MySanctionService(
    private val sanctionRepository: SanctionRepository,
    private val memberReportRepository: MemberReportRepository,
) {

    /** 주어진 시각에 유효한 내 제재 중 가장 무거운 것 하나를 반환한다 (없으면 sanction=null). */
    @Transactional(readOnly = true)
    fun getMySanction(memberId: Long, now: LocalDateTime): MySanctionResponse {
        val effective = sanctionRepository.findAllByMemberIdAndStatus(memberId, SanctionStatus.ACTIVE)
            .filter { it.isEffectiveAt(now) }
            .maxWithOrNull(compareBy({ it.level }, { it.id }))
            ?: return MySanctionResponse(sanction = null)

        return MySanctionResponse(sanction = toActiveSanctionResponse(effective))
    }

    private fun toActiveSanctionResponse(sanction: Sanction): ActiveSanctionResponse {
        // 사유는 근거 신고의 카테고리만 — 직권 제재(신고 없음)는 null.
        val reason = sanction.memberReportId
            ?.let { memberReportRepository.findById(it).getOrNull() }
            ?.reason

        return ActiveSanctionResponse(
            level = sanction.level.name,
            levelDescription = sanction.level.description,
            reason = reason?.code,
            reasonDescription = reason?.description,
            startsAt = sanction.startsAt,
            endsAt = sanction.endsAt,
        )
    }
}
