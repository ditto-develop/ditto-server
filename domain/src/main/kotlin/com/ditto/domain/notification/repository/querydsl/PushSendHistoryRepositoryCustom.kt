package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import java.time.LocalDate
import java.time.LocalDateTime

interface PushSendHistoryRepositoryCustom {

    fun deleteCreatedBefore(threshold: LocalDateTime): Long

    /** 그 주(월~일) 이력을 유형·건너뛴 이유·실패 기기 수로 묶은 수. 회원 조건은 받는 회원에게 건다. */
    fun countHistoriesInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<PushSendHistoryCount>
}
