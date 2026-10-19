package com.ditto.domain.launchnotification.repository.querydsl

import com.ditto.domain.launchnotification.entity.LaunchPlatform
import java.time.LocalDateTime

interface LaunchNotificationRequestRepositoryCustom {

    /**
     * 아직 안내를 안 보낸 [upToId] 이하 신청에 발송 시각을 찍는다 — 어드민 "발송 완료".
     *
     * 상한 id를 받는 이유: 어드민이 목록을 내보낸 뒤 완료를 누르기 전에 들어온 신청은 메일을 못 받았다.
     * 내보낸 시점의 마지막 id까지만 찍어야 그 사람들이 "보냄"으로 묻히지 않는다.
     *
     * @return 이번 호출로 발송 완료가 된 건수
     */
    fun markNotified(platform: LaunchPlatform, upToId: Long, at: LocalDateTime): Long

    /** 발송 후 보유 기간이 지난 신청을 지운다. @return 지운 건수 */
    fun deleteNotifiedBefore(threshold: LocalDateTime): Long
}
