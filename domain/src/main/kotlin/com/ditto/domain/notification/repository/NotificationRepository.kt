package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.querydsl.NotificationRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    /**
     * 내 알림 단건. 남의 알림을 id로 찍어 읽음 처리하는 것을 막으려고 회원 조건을 함께 둔다 —
     * 조회 후 소유자를 비교하는 방식은 비교를 빠뜨릴 수 있다.
     * 지운 알림은 없는 것으로 다룬다.
     */
    fun findByIdAndMemberIdAndDeletedAtIsNull(id: Long, memberId: Long): Notification?

    /**
     * 같은 사건을 두 번 알리지 않기 위한 존재 검사. 스케줄러가 같은 대상을 다시 집어와도 알림은 하나다.
     * 대상이 없으면 "같은 사건"을 정의할 수 없으므로 `targetId`는 필수다.
     * 지운 알림도 함께 센다 — 지웠다고 다시 알리면 지울 때마다 같은 알림이 돌아온다.
     */
    fun existsByMemberIdAndTypeAndTargetId(memberId: Long, type: NotificationType, targetId: Long): Boolean
}
