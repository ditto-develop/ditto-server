package com.ditto.api.admin.qa.dto

import com.ditto.api.admin.qa.QaTimeMoveWarning
import com.ditto.api.admin.qa.weekendOf
import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** 시간 조정 화면은 시각을 직접 고르므로 제출 직전에 화면이 판단한다. 기준과 문구는 QA 콘솔의 [QaTimeMoveWarning] 과 같다. */
class QaTimeMoveGuide(
    val now: LocalDateTime,
    val groupDeadline: LocalDateTime,
    val chatEnd: LocalDateTime,
    val disableConfirmMessage: String?,
) {
    val backToPastMessage: String = QaTimeMoveWarning.BACK_TO_PAST.message
    val groupDeadlineMessage: String = QaTimeMoveWarning.PAST_GROUP_DEADLINE.message
    val chatEndMessage: String = QaTimeMoveWarning.PAST_CHAT_END.message
    val irreversibleNotice: String = QaTimeMoveWarning.IRREVERSIBLE_NOTICE

    companion object {
        fun of(now: LocalDateTime, realNow: LocalDateTime): QaTimeMoveGuide {
            val week = OperationWeek.containing(now.toLocalDate())
            return QaTimeMoveGuide(
                now = now,
                groupDeadline = GroupResponseDeadline.deadlineOf(week),
                chatEnd = weekendOf(week).expiresAt,
                disableConfirmMessage = QaTimeMoveWarning.disableConfirmMessageOf(now, realNow),
            )
        }
    }
}
