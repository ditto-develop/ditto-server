package com.ditto.api.match

import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 그룹 초대 응답 마감. 그 주 채팅이 열리는 금요일 00:00이다.
 *
 * 응답 차단([MatchWeekPolicy.validateGroupResponseOpen]), 미응답 자동 거절, 미성사 알림이 같은 마감을 본다.
 * 셋이 따로 계산하면 "인원 미달 알림을 받은 그룹이 그 뒤 수락으로 성사되는" 어긋남이 생긴다.
 */
object GroupResponseDeadline {

    /** [now]가 속한 운영 주의 마감이 지났는가. */
    fun isPassed(now: LocalDateTime): Boolean = ChatPeriod.weekendOf(now).isOpenedAt(now)

    /** 마감이 지난 가장 최근 운영 주의 월요일. 이번 주 마감 전이면 지난 주다. */
    fun latestPassedWeekStartedOn(now: LocalDateTime): LocalDate {
        val thisWeekStartedOn = OperationWeek.containing(now.toLocalDate()).startedOn
        return if (isPassed(now)) thisWeekStartedOn else thisWeekStartedOn.minusWeeks(1)
    }
}
