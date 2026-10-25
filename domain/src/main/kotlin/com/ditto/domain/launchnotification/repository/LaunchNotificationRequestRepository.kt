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

    fun countByNotifiedAtIsNull(): Long

    fun countByPlatformAndNotifiedAtIsNotNull(platform: LaunchPlatform): Long

    fun findFirstByPlatformAndNotifiedAtIsNullOrderByIdDesc(platform: LaunchPlatform): LaunchNotificationRequest?
}
