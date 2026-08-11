package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.querydsl.NotificationRepositoryCustom
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    /**
     * 내 알림 단건. 남의 알림을 id로 찍어 읽음 처리하는 것을 막으려고 회원 조건을 함께 둔다 —
     * 조회 후 소유자를 비교하는 방식은 비교를 빠뜨릴 수 있다.
     */
    fun findByIdAndMemberId(id: Long, memberId: Long): Notification?

    /** 홈 헤더 벨 배지 — 보관 기간 안의 안읽은 알림 수. 목록에 안 보이는 알림을 세지 않도록 창을 맞춘다. */
    fun countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(memberId: Long, from: LocalDateTime): Long

    /**
     * 같은 사건을 두 번 알리지 않기 위한 존재 검사. 스케줄러가 같은 대상을 다시 집어와도 알림은 하나다.
     * 대상이 없으면 "같은 사건"을 정의할 수 없으므로 `targetId`는 필수다.
     */
    fun existsByMemberIdAndTypeAndTargetId(memberId: Long, type: NotificationType, targetId: Long): Boolean
}
