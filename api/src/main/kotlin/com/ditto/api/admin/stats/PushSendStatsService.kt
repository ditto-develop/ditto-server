package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.notification.repository.PushSendHistoryRepository
import com.ditto.domain.notification.repository.querydsl.PushSendHistoryCount
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// 알림은 매칭 유형과 관계없어서 매칭 유형 조건은 걸지 않는다.
@Service
class PushSendStatsService(
    private val pushSendHistoryRepository: PushSendHistoryRepository,
) {

    @Transactional(readOnly = true)
    fun countHistories(filter: StatsFilter, week: OperationWeek, today: LocalDate): List<PushSendHistoryCount> =
        pushSendHistoryRepository.countHistoriesInWeek(week.startedOn, filter.toMemberFilter(today))
}
