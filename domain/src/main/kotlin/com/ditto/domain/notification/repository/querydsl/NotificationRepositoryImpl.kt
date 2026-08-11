package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.entity.QNotification.notification
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class NotificationRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : NotificationRepositoryCustom {

    override fun findByMemberIdWithCursor(
        memberId: Long,
        category: NotificationCategory?,
        cursor: Long?,
        size: Int,
        from: LocalDateTime,
    ): List<Notification> =
        queryFactory
            .selectFrom(notification)
            .where(
                notification.memberId.eq(memberId),
                notification.createdAt.goe(from),
                category?.let { notification.type.`in`(NotificationType.of(it)) },
                cursor?.let { notification.id.lt(it) },
            )
            .orderBy(notification.id.desc())
            .limit(size.toLong())
            .fetch()

    @Transactional
    override fun markAllRead(memberId: Long, at: LocalDateTime): Long =
        queryFactory
            .update(notification)
            .set(notification.readAt, at)
            .set(notification.updatedAt, at)
            .where(
                notification.memberId.eq(memberId),
                notification.readAt.isNull,
            )
            .execute()

    @Transactional
    override fun deleteUnread(memberId: Long, type: NotificationType, targetId: Long): Long =
        queryFactory
            .delete(notification)
            .where(
                notification.memberId.eq(memberId),
                notification.type.eq(type),
                notification.targetId.eq(targetId),
                notification.readAt.isNull,
            )
            .execute()

    @Transactional
    override fun deleteAllByMemberId(memberId: Long): Long =
        queryFactory
            .delete(notification)
            .where(notification.memberId.eq(memberId))
            .execute()

    @Transactional
    override fun deleteCreatedBefore(threshold: LocalDateTime): Long =
        queryFactory
            .delete(notification)
            .where(notification.createdAt.lt(threshold))
            .execute()
}
