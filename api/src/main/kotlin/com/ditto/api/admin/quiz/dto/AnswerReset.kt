package com.ditto.api.admin.quiz.dto

/** 셋 전체 답·진행 초기화 결과. 함께 지운 매칭 기록을 포함한다. */
class AnswerResetSummary(
    val matchingErase: MatchingEraseSummary,
    val participantCount: Int,
    val answerCount: Int,
) {
    fun toDisplayText(): String {
        val answerResetText = "참여자 ${participantCount}명 · 답 ${answerCount}개"
        val matchingEraseText = matchingErase.toDisplayText().takeUnless { matchingErase.isNothingErased }
        return listOfNotNull(answerResetText, matchingEraseText).joinToString(" · ")
    }
}

/** 퀴즈셋 상세 QA 도구 카드의 답·진행 초기화 상태. 실회원 답도 지워지므로 실회원 수를 따로 센다. */
class AnswerResetPreview(
    val participantCount: Int,
    val realParticipantCount: Int,
    val isCurrentWeek: Boolean,
    val isQuizPeriodOver: Boolean,
)

/** 참여 현황 화면에 회원별 초기화 버튼을 띄울지 정하는 상태. */
class MemberAnswerResetAvailability(
    val refusal: MemberAnswerResetRefusal?,
    val isQuizPeriodOver: Boolean,
) {
    val isResettable: Boolean get() = refusal == null
}

enum class MemberAnswerResetRefusal(val message: String) {
    NOT_CURRENT_WEEK("회원별 답·진행 초기화는 이번 주 퀴즈셋만 할 수 있습니다."),
    AFTER_MATCHING("매칭이 진행된 퀴즈셋은 회원별로 초기화할 수 없습니다. 퀴즈셋 상세 QA 도구에서 전체 답·진행 초기화를 쓰세요."),
}
