package com.ditto.api.admin.qa

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** 서버 시각을 옮기기 전에 확인받을 일. 마감을 넘기는 이동은 스케줄러가 1분 안에 처리해 되돌릴 수 없다. */
enum class QaTimeMoveWarning(val message: String, val isIrreversible: Boolean) {
    BACK_TO_PAST(
        "지난 시점으로 돌아갑니다. 이미 열리거나 끝난 방, 거절된 그룹 초대, 보낸 알림은 그대로입니다.",
        isIrreversible = false,
    ),
    PAST_GROUP_DEADLINE(
        "그룹 응답 마감을 넘깁니다. 응답하지 않은 초대가 1분 안에 거절되고 인원이 모자란 그룹에 미성사 알림이 갑니다.",
        isIrreversible = true,
    ),
    PAST_CHAT_END(
        "채팅 마감을 넘깁니다. 열린 방이 1분 안에 마감되고 평가가 열리며, 이번 주가 다음 주로 바뀝니다.",
        isIrreversible = true,
    ),
    ;

    companion object {
        const val IRREVERSIBLE_NOTICE = "되돌릴 수 없습니다."
        const val MOVE_QUESTION = "옮길까요?"
        private const val DISABLE_QUESTION = "끌까요?"

        // 끄기 버튼만 눌렀는데 마감 경고가 뜨면 이유를 모르니, 실제 시각으로 돌아간다는 말을 앞에 붙인다.
        private const val DISABLE_LEAD = "끄면 실제 시각으로 돌아갑니다."

        fun of(week: OperationWeek, now: LocalDateTime, target: LocalDateTime): List<QaTimeMoveWarning> {
            if (target < now) return listOf(BACK_TO_PAST)
            return listOfNotNull(
                PAST_GROUP_DEADLINE.takeIf { crosses(now, target, GroupResponseDeadline.deadlineOf(week)) },
                PAST_CHAT_END.takeIf { crosses(now, target, weekendOf(week).expiresAt) },
            )
        }

        fun confirmMessageOf(warnings: List<QaTimeMoveWarning>, question: String = MOVE_QUESTION): String? {
            if (warnings.isEmpty()) return null
            val irreversible = IRREVERSIBLE_NOTICE.takeIf { warnings.any { it.isIrreversible } }
            return (warnings.map { it.message } + listOfNotNull(irreversible, question)).joinToString(" ")
        }

        /** 시간 조정을 끄면 실제 시각으로 옮겨 가는 것과 같아서 같은 경고를 쓴다. */
        fun disableConfirmMessageOf(now: LocalDateTime, realNow: LocalDateTime): String? =
            confirmMessageOf(of(OperationWeek.containing(now.toLocalDate()), now, realNow), DISABLE_QUESTION)
                ?.let { "$DISABLE_LEAD $it" }

        private fun crosses(now: LocalDateTime, target: LocalDateTime, boundary: LocalDateTime): Boolean =
            now < boundary && target >= boundary
    }
}
