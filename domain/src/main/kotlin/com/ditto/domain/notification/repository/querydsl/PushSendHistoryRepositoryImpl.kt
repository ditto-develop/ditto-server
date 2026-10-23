package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.notification.entity.QPushSendHistory.pushSendHistory
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
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
}
