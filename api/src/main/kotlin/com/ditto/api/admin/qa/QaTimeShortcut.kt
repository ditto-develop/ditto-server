package com.ditto.api.admin.qa

import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** QA 콘솔의 시각 바로가기. 그룹 응답 마감과 채팅 개방·마감이 모두 그 주 주말([ChatPeriod])에 맞춰 움직인다. */
enum class QaTimeShortcut(
    val label: String,
    val confirmMessage: String?,
    private val pickFrom: (ChatPeriod) -> LocalDateTime,
) {
    BEFORE_GROUP_DEADLINE("그룹 응답 마감 직전", null, { it.opensAt.minusMinutes(10) }),

    CHAT_OPEN("채팅 개방 직후", null, { it.opensAt.plusMinutes(1) }),

    CHAT_ENDED(
        "채팅 마감 직후",
        "열린 방이 1분 안에 마감되고 평가가 열립니다. 되돌릴 수 없습니다. 옮길까요?",
        { it.expiresAt.plusMinutes(1) },
    ),
    ;

    fun dateTimeIn(week: OperationWeek): LocalDateTime = pickFrom(ChatPeriod.weekendOf(week.startedOn.atStartOfDay()))
}
