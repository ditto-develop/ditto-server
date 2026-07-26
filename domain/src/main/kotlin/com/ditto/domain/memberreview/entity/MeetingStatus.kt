package com.ditto.domain.memberreview.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 평가 대상과 실제로 어디까지 진행했는지. 응답 시 필수로 하나를 고른다.
 *
 * - [code]: FE와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [description]: 평가 화면 문구
 *
 * [NO_SHOW]는 노쇼 **신호**로만 보존한다 — 이 값 하나로 제재를 집행하지 않고,
 * 운영자가 확정한 노쇼 사건만 누적 대상이 된다.
 *
 * 값 추가만 허용하며, 이미 배포된 값의 이름/코드 변경·삭제는 금지한다.
 */
enum class MeetingStatus(
    val code: String,
    val description: String,
) {
    MET("met", "만났어요"),
    APPOINTMENT_MADE("appointment-made", "약속 잡았어요"),
    CHAT_ONLY("chat-only", "채팅만 했어요"),
    NO_SHOW("no-show", "노쇼 당했어요"),
    ;

    companion object {
        fun from(code: String): MeetingStatus =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER)
    }
}
