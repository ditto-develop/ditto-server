package com.ditto.domain.sanction

import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.withId
import java.time.LocalDateTime

object SanctionFixture {

    fun create(
        memberId: Long = 1L,
        origin: SanctionOrigin = SanctionOrigin.MANUAL,
        level: SanctionLevel = SanctionLevel.SUSPENSION,
        startsAt: LocalDateTime = LocalDateTime.of(2026, 7, 13, 0, 0),
        endsAt: LocalDateTime? = if (level == SanctionLevel.PERMANENT_BAN) null else startsAt.plusDays(14),
        createdBy: Long = 99L,
        creatorName: String = "관리자",
        memberReportId: Long? = null,
        note: String? = null,
        id: Long = 0L,
    ): Sanction = Sanction.impose(
        memberId = memberId,
        origin = origin,
        level = level,
        startsAt = startsAt,
        endsAt = endsAt,
        createdBy = createdBy,
        creatorName = creatorName,
        memberReportId = memberReportId,
        note = note,
    ).withId(id)
}
