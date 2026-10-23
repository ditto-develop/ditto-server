package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.nonAdminMatching
import com.ditto.domain.notification.entity.QPushSendHistory.pushSendHistory
import com.ditto.domain.system.OperationWeek
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(readOnly = true)
class PushSendHistoryRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : PushSendHistoryRepositoryCustom {

    @Transactional
    override fun deleteCreatedBefore(threshold: LocalDateTime): Long =
        queryFactory
            .delete(pushSendHistory)
            .where(pushSendHistory.createdAt.lt(threshold))
            .execute()

    override fun countHistoriesInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<PushSendHistoryCount> {
        val createdRange = OperationWeek(weekStartedOn).dateTimeRange
        val countExpression = pushSendHistory.count()
        return queryFactory
            .select(
                pushSendHistory.notificationType,
                pushSendHistory.skipReason,
                pushSendHistory.failedDeviceCount,
                countExpression,
            )
            .from(pushSendHistory)
            .join(member).on(member.id.eq(pushSendHistory.memberId))
            .where(
                pushSendHistory.createdAt.goe(createdRange.start),
                pushSendHistory.createdAt.lt(createdRange.endExclusive),
                member.nonAdminMatching(filter),
            )
            .groupBy(
                pushSendHistory.notificationType,
                pushSendHistory.skipReason,
                pushSendHistory.failedDeviceCount,
            )
            .fetch()
            .map { row ->
                PushSendHistoryCount(
                    notificationType = row.get(pushSendHistory.notificationType)
                        ?: throw IllegalStateException("group by 키 notificationType 이 비었다"),
                    skipReason = row.get(pushSendHistory.skipReason),
                    failedDeviceCount = row.get(pushSendHistory.failedDeviceCount) ?: 0,
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }
}
