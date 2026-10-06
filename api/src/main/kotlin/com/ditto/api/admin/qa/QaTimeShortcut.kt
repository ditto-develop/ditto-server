package com.ditto.api.admin.qa

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.quiz.entity.QuizResponsePeriod
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

private const val JUST_AFTER_MINUTES = 1L
private const val JUST_BEFORE_MINUTES = 10L

/**
 * QA 콘솔의 시각 바로가기. 기준 시각은 앱의 기간 정의와 알림 설정에서 가져온다.
 * note 는 옮겨도 기대한 일이 일어나지 않는 경우를 알린다. 주간 알림 크론과 매칭 배치는 실제 시각에만 돈다.
 */
enum class QaTimeShortcut(
    val label: String,
    val note: String?,
    private val pickIn: (OperationWeek, QaChatReminderLeadHours) -> LocalDateTime,
) {
    QUIZ_OPENED("퀴즈 열린 직후", "퀴즈 오픈 알림은 안 감", { week, _ ->
        QuizResponsePeriod(week).startsAt.plusMinutes(JUST_AFTER_MINUTES)
    }),

    QUIZ_CLOSING("퀴즈 마감 직전", "마감 임박 알림은 안 감", { week, _ ->
        QuizResponsePeriod(week).endsAt.minusMinutes(JUST_BEFORE_MINUTES)
    }),

    QUIZ_CLOSED("퀴즈 마감 직후", "매칭은 [자동 매칭 실행]으로", { week, _ ->
        matchingDayOf(week).plusMinutes(JUST_AFTER_MINUTES)
    }),

    GROUP_DEADLINE_CLOSING("그룹 응답 마감 직전", null, { week, _ ->
        GroupResponseDeadline.deadlineOf(week).minusMinutes(JUST_BEFORE_MINUTES)
    }),

    CHAT_OPENED("채팅방 열린 직후", null, { week, _ ->
        weekendOf(week).opensAt.plusMinutes(JUST_AFTER_MINUTES)
    }),

    CHAT_FIRST_MESSAGE_NOTIFIED("첫 인사 알림 직후", "대화 없는 방만, 방마다 한 번", { week, leadHours ->
        weekendOf(week).opensAt.plusHours(leadHours.firstMessageHours).plusMinutes(JUST_AFTER_MINUTES)
    }),

    CHAT_ENDING_SOON_NOTIFIED("마감 임박 알림 직후", "방마다 한 번", { week, leadHours ->
        weekendOf(week).expiresAt.minusHours(leadHours.endingSoonHours).plusMinutes(JUST_AFTER_MINUTES)
    }),

    CHAT_ENDED("채팅 마감 직후", null, { week, _ ->
        weekendOf(week).expiresAt.plusMinutes(JUST_AFTER_MINUTES)
    }),
    ;

    fun dateTimeIn(week: OperationWeek, leadHours: QaChatReminderLeadHours): LocalDateTime = pickIn(week, leadHours)
}

class QaChatReminderLeadHours(
    val firstMessageHours: Long,
    val endingSoonHours: Long,
)

/** 지금 서버 시각에서 정해진 만큼 뒤로 옮기는 버튼. */
enum class QaTimeStep(val label: String, val hours: Long) {
    ONE_HOUR("+1시간", 1),
    ONE_DAY("+1일", 24),
}

internal fun weekendOf(week: OperationWeek): ChatPeriod = ChatPeriod.weekendOf(week.startedOn.atStartOfDay())

/** 퀴즈 응답이 끝난 다음 날이 매칭 기간이다. */
private fun matchingDayOf(week: OperationWeek): LocalDateTime =
    QuizResponsePeriod(week).endsAt.toLocalDate().plusDays(1).atStartOfDay()
