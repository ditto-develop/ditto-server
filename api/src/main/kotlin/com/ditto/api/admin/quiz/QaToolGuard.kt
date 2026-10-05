package com.ditto.api.admin.quiz

import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.api.match.MatchWeekPolicy
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.stereotype.Component

/** 퀴즈셋 QA 도구가 공통으로 지키는 조건. 스위치와 이번 주 셋 여부를 한 곳에서 본다. */
@Component
class QaToolGuard(
    private val adminQaToolsProperties: AdminQaToolsProperties,
    private val matchWeekPolicy: MatchWeekPolicy,
) {
    val isEnabled: Boolean get() = adminQaToolsProperties.enabled

    fun isCurrentWeek(quizSet: QuizSet): Boolean = matchWeekPolicy.isCurrentWeek(quizSet)

    fun validateEnabled() {
        if (!isEnabled) {
            throw WarnException(ErrorCode.FORBIDDEN, "QA 도구가 꺼져 있습니다. QA 도구 스위치를 켠 환경에서만 쓸 수 있습니다.")
        }
    }

    // 다른 주 셋은 다시 돌려도 앱에 보이지 않고, 그대로 두면 다음 목요일 배치가 다시 매칭해 결과 알림을 보낸다.
    fun validateCurrentWeek(quizSet: QuizSet) {
        if (!isCurrentWeek(quizSet)) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이번 주 퀴즈셋만 초기화할 수 있습니다. 정리하려면 퀴즈셋 강제 삭제를 쓰세요.")
        }
    }
}
