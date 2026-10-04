package com.ditto.api.admin.qa

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.quiz.entity.QuizResponsePeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** QA 콘솔의 시각 바로가기. 기준 시각은 앱이 쓰는 기간 정의에서 그대로 가져온다. */
enum class QaTimeShortcut(
    val label: String,
    val confirmMessage: String?,
    private val pickIn: (OperationWeek) -> LocalDateTime,
) {
    QUIZ_ANSWERING("퀴즈 응답 마감 직전", null, { QuizResponsePeriod(it).endsAt.minusMinutes(10) }),

    BEFORE_GROUP_DEADLINE("그룹 응답 마감 직전", null, { GroupResponseDeadline.deadlineOf(it).minusMinutes(10) }),

    CHAT_OPEN("채팅 개방 직후", null, { weekendOf(it).opensAt.plusMinutes(1) }),

    CHAT_ENDED(
        "채팅 마감 직후",
        "열린 방이 1분 안에 마감되고 평가가 열립니다. 되돌릴 수 없습니다. 옮길까요?",
        { weekendOf(it).expiresAt.plusMinutes(1) },
    ),
    ;

    fun dateTimeIn(week: OperationWeek): LocalDateTime = pickIn(week)
}

private fun weekendOf(week: OperationWeek): ChatPeriod = ChatPeriod.weekendOf(week.startedOn.atStartOfDay())
