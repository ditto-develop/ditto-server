package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import java.time.LocalDate
import java.time.LocalDateTime

interface PushSendHistoryRepositoryCustom {

    /** 보관 기간이 지난 이력을 지우고 지운 건수를 돌려준다. */
    fun deleteCreatedBefore(threshold: LocalDateTime): Long

    /** 그 주(월~일)에 남은 이력을 유형·건너뛴 이유·기기 수·실패 기기 수로 묶은 수. 회원 조건은 받는 회원에게 건다. */
    fun countHistoriesInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<PushSendHistoryCount>
}
