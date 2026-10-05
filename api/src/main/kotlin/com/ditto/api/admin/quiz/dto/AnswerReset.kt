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

/**
 * 퀴즈셋 상세 QA 도구 카드가 보여 줄 상태. 실회원 답도 지워지므로 실회원 수를 따로 센다.
 * 퀴즈 기간이 끝났으면 초기화해도 앱에서 다시 풀 수 없어 시간 오버라이드 안내를 띄운다.
 */
class AnswerResetPreview(
    val participantCount: Int,
    val realParticipantCount: Int,
    val isResettable: Boolean,
    val isQuizPeriodOver: Boolean,
)

/** 참여 현황 화면 행마다 붙는 회원별 초기화 버튼 상태. */
class MemberAnswerResetOption(
    val isResettable: Boolean,
    val isQuizPeriodOver: Boolean,
)
