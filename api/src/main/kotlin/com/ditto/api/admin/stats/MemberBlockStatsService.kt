package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MemberBlockStatsService(
    private val memberBlockRepository: MemberBlockRepository,
) {

    @Transactional(readOnly = true)
    fun countBlocks(filter: StatsFilter, week: OperationWeek, today: LocalDate): Long =
        memberBlockRepository.countBlocksCreatedInWeek(
            week.startedOn,
            filter.matchingType.quizMatchingType,
            filter.toMemberFilter(today),
        )
}
