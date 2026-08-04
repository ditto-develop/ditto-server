package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.MemberNotificationSetting
import org.springframework.data.jpa.repository.JpaRepository

interface MemberNotificationSettingRepository : JpaRepository<MemberNotificationSetting, Long> {

    fun findByMemberId(memberId: Long): MemberNotificationSetting?
}
