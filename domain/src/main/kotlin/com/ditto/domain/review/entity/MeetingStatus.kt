package com.ditto.domain.review.entity

/**
 * 평가 대상과 실제로 어디까지 진행했는지. 응답 시 필수로 하나를 고른다.
 *
 * `Gender`·`ChatMessageType`처럼 **enum 이름을 그대로 주고받는다** — 별도 `code`를 두지 않아
 * DTO가 문자열을 받아 다시 매핑하는 단계가 없다. [description]은 평가 화면 문구다.
 *
 * [NO_SHOW]는 노쇼 **신호**로만 보존한다 — 이 값 하나로 제재를 집행하지 않고,
 * 운영자가 확정한 노쇼 사건만 누적 대상이 된다.
 *
 * 값 추가만 허용하며, 이미 배포된 값의 이름 변경·삭제는 금지한다.
 */
enum class MeetingStatus(
    val description: String,
) {
    MET("만났어요"),
    APPOINTMENT_MADE("약속 잡았어요"),
    CHAT_ONLY("채팅만 했어요"),
    NO_SHOW("노쇼 당했어요"),
}
