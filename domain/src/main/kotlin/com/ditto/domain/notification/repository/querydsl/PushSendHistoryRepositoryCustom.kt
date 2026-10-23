package com.ditto.domain.notification.repository.querydsl

import java.time.LocalDateTime

interface PushSendHistoryRepositoryCustom {

    /** 보관 기간이 지난 이력을 지우고 지운 건수를 돌려준다. */
    fun deleteCreatedBefore(threshold: LocalDateTime): Long
}
