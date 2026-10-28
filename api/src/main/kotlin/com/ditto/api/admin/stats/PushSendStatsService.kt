package com.ditto.api.admin.stats

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.PushDeviceCoverage
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
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun countHistories(filter: StatsFilter, week: OperationWeek, today: LocalDate): List<PushSendHistoryCount> =
        pushSendHistoryRepository.countHistoriesInWeek(week.startedOn, filter.toMemberFilter(today))

    // 기기는 지금 등록된 것만 알 수 있어 회차와 관계없이 오늘 기준이다. 더미는 기기가 없어 뺀다.
    @Transactional(readOnly = true)
    fun findPushDeviceCoverage(filter: StatsFilter, today: LocalDate): PushDeviceCoverage =
        memberRepository.countActiveMembersWithPushDevice(filter.toMemberFilter(today), DummyMarker.NICKNAME_PREFIX)
}
