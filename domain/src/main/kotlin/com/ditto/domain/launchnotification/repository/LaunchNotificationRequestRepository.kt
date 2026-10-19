package com.ditto.domain.launchnotification.repository

import com.ditto.domain.launchnotification.entity.LaunchNotificationRequest
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.querydsl.LaunchNotificationRequestRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface LaunchNotificationRequestRepository :
    JpaRepository<LaunchNotificationRequest, Long>,
    LaunchNotificationRequestRepositoryCustom {

    fun findByEmailAndPlatform(email: String, platform: LaunchPlatform): LaunchNotificationRequest?

    fun countByPlatformAndNotifiedAtIsNull(platform: LaunchPlatform): Long

    fun countByPlatformAndNotifiedAtIsNotNull(platform: LaunchPlatform): Long

    /** 내보내기 대상 — 아직 안내를 안 보낸 [upToId] 이하 신청, 오래된 순. */
    fun findAllByPlatformAndNotifiedAtIsNullAndIdLessThanEqualOrderByIdAsc(
        platform: LaunchPlatform,
        upToId: Long,
    ): List<LaunchNotificationRequest>

    fun findFirstByPlatformAndNotifiedAtIsNullOrderByIdDesc(platform: LaunchPlatform): LaunchNotificationRequest?
}
