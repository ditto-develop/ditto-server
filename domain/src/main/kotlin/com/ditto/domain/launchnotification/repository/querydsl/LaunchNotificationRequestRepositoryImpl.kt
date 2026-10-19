package com.ditto.domain.launchnotification.repository.querydsl

import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.entity.QLaunchNotificationRequest.launchNotificationRequest
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class LaunchNotificationRequestRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : LaunchNotificationRequestRepositoryCustom {

    // 벌크 UPDATE 는 auditing 을 거치지 않아 수정일시를 직접 찍는다.
    @Transactional
    override fun markNotified(platform: LaunchPlatform, upToId: Long, at: LocalDateTime): Long =
        queryFactory
            .update(launchNotificationRequest)
            .set(launchNotificationRequest.notifiedAt, at)
            .set(launchNotificationRequest.updatedAt, at)
            .where(
                launchNotificationRequest.platform.eq(platform),
                launchNotificationRequest.notifiedAt.isNull,
                launchNotificationRequest.id.loe(upToId),
            )
            .execute()

    @Transactional
    override fun deleteNotifiedBefore(threshold: LocalDateTime): Long =
        queryFactory
            .delete(launchNotificationRequest)
            .where(launchNotificationRequest.notifiedAt.lt(threshold))
            .execute()
}
