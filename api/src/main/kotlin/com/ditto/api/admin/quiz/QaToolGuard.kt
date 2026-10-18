package com.ditto.api.admin.quiz

import com.ditto.api.match.MatchWeekPolicy
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.stereotype.Component

/** 퀴즈셋 QA 도구는 이번 주 셋에만 쓴다. */
@Component
class QaToolGuard(
    private val matchWeekPolicy: MatchWeekPolicy,
) {
    fun isCurrentWeek(quizSet: QuizSet): Boolean = matchWeekPolicy.isCurrentWeek(quizSet)

    // 다른 주 셋은 다시 돌려도 앱에 보이지 않고, 그대로 두면 다음 목요일 배치가 다시 매칭해 결과 알림을 보낸다.
    fun validateCurrentWeek(quizSet: QuizSet) {
        if (!isCurrentWeek(quizSet)) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이번 주 퀴즈셋만 초기화할 수 있습니다. 정리하려면 퀴즈셋 강제 삭제를 쓰세요.")
        }
    }
}
