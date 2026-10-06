package com.ditto.api.admin.qa

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.quiz.entity.QuizResponsePeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** QA 콘솔의 시각 바로가기. 기준 시각은 앱이 쓰는 기간 정의와 알림 설정에서 그대로 가져온다. */
enum class QaTimeShortcut(
    val label: String,
    val confirmMessage: String?,
    private val pickIn: (OperationWeek, QaChatReminderLeadHours) -> LocalDateTime,
) {
    QUIZ_OPEN("퀴즈 열린 직후", null, { week, _ -> QuizResponsePeriod(week).startsAt.plusMinutes(1) }),

    QUIZ_ANSWERING("퀴즈 마감 직전", null, { week, _ -> QuizResponsePeriod(week).endsAt.minusMinutes(10) }),

    MATCHING_PERIOD("매칭 기간 시작", null, { week, _ -> QuizResponsePeriod(week).endsAt.plusSeconds(1).plusMinutes(1) }),

    BEFORE_GROUP_DEADLINE("그룹 응답 마감 직전", null, { week, _ -> GroupResponseDeadline.deadlineOf(week).minusMinutes(10) }),

    CHAT_OPEN("채팅방 열린 직후", null, { week, _ -> weekendOf(week).opensAt.plusMinutes(1) }),

    NO_MESSAGE_REMINDER(
        "첫 메시지 리마인드 직후",
        null,
        { week, leadHours -> weekendOf(week).opensAt.plusHours(leadHours.noMessage).plusMinutes(1) },
    ),

    ENDING_SOON(
        "종료 임박 알림 직후",
        null,
        { week, leadHours -> weekendOf(week).expiresAt.minusHours(leadHours.endingSoon).plusMinutes(1) },
    ),

    CHAT_ENDED(
        "채팅 마감 직후",
        CHAT_END_CONFIRM_MESSAGE,
        { week, _ -> weekendOf(week).expiresAt.plusMinutes(1) },
    ),
    ;

    fun dateTimeIn(week: OperationWeek, leadHours: QaChatReminderLeadHours): LocalDateTime = pickIn(week, leadHours)
}

/** 채팅 알림이 개방·마감으로부터 몇 시간 떨어져 나가는지. 알림 쪽 설정을 그대로 받는다. */
class QaChatReminderLeadHours(
    val noMessage: Long,
    val endingSoon: Long,
)

/** 지금 서버 시각에서 상대로 옮기는 버튼. */
enum class QaTimeStep(val label: String, val hours: Long) {
    ONE_HOUR("+1시간", 1),
    ONE_DAY("+1일", 24),
}

const val CHAT_END_CONFIRM_MESSAGE = "열린 방이 1분 안에 마감되고 평가가 열립니다. 되돌릴 수 없습니다. 옮길까요?"

private fun weekendOf(week: OperationWeek): ChatPeriod = ChatPeriod.weekendOf(week.startedOn.atStartOfDay())
