package com.ditto.api.review.dto

import com.ditto.domain.review.entity.MeetingStatus

/**
 * 대상 한 명에 대한 **최종 제출**. 수정·임시 저장 단계는 없다.
 *
 * @property meetingStatus `MET` / `APPOINTMENT_MADE` / `CHAT_ONLY` / `NO_SHOW`
 * @property rating 1~5 정수
 * @property comment 선택. 공백만 보내면 미입력으로 정규화된다
 * @property wantsOneToOneRematch 그룹 평가에서만 필수 — 1:1 평가에는 재매칭 개념이 없어 보내면 거부한다
 */
data class ReviewAnswerSubmitRequest(
    val meetingStatus: MeetingStatus,
    val rating: Int,
    val comment: String? = null,
    val wantsOneToOneRematch: Boolean? = null,
)
