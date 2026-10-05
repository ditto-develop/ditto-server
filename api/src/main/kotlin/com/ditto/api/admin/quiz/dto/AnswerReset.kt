package com.ditto.api.admin.quiz.dto

/** 셋 전체 답·진행 초기화 결과. 매칭 기록도 함께 지우므로 그 결과를 같이 담는다. */
class AnswerResetSummary(
    val matching: MatchingEraseSummary,
    val participantCount: Int,
    val answerCount: Int,
) {
    fun toDisplayText(): String {
        val answerText = "참여자 ${participantCount}명 · 답 ${answerCount}개"
        val matchingText = matching.toDisplayText().takeUnless { it == MatchingEraseSummary.NOTHING_ERASED }
        return listOfNotNull(answerText, matchingText).joinToString(" · ")
    }
}

/** 셋 전체 초기화 미리보기. 실회원 답도 지워지므로 실회원 수를 따로 센다. */
class AnswerResetPreview(
    val participantCount: Int,
    val realParticipantCount: Int,
)
