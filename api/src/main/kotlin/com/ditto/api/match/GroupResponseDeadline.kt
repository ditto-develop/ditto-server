package com.ditto.api.match

import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/**
 * 그룹 초대 응답 마감. 그 주 채팅이 열리는 금요일 00:00이다.
 *
 * 응답 차단, 미응답 자동 거절, 미성사 알림이 모두 이 마감을 쓴다. 따로 계산하면
 * 인원 미달 알림을 받은 그룹이 나중에 수락으로 성사될 수 있다.
 */
object GroupResponseDeadline {

    fun hasPassed(week: OperationWeek, now: LocalDateTime): Boolean = now >= deadlineOf(week)

    /** 마감이 가장 최근에 지난 주. 이번 주 마감 전이면 지난 주다. */
    fun latestClosedWeek(now: LocalDateTime): OperationWeek {
        val thisWeek = OperationWeek.containing(now.toLocalDate())
        if (hasPassed(thisWeek, now)) {
            return thisWeek
        }
        return OperationWeek(thisWeek.startedOn.minusWeeks(1))
    }

    private fun deadlineOf(week: OperationWeek): LocalDateTime =
        ChatPeriod.weekendOf(week.startedOn.atStartOfDay()).opensAt
}
