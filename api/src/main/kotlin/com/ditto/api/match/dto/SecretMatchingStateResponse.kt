package com.ditto.api.match.dto

import com.ditto.api.match.SecretMatchingStage
import com.ditto.api.match.SecretMatchingState
import com.ditto.domain.system.OperationWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class SecretMatchingStateResponse(
    val state: SecretMatchingState,
    /** 다음 시크릿 매칭 라운드 시각. FE 가 "금/토요일" 문구를 고른다. 남은 라운드가 없거나 카드가 없으면 null */
    val nextRoundAt: LocalDateTime?,
    /** 이번 주 1:1 퀴즈셋. 목~토에는 current-week 목록이 비어 있어 퀴즈로 바로 들어갈 때 쓴다. 셋이 없으면 null */
    val quizSetId: Long?,
    val weekStartedOn: LocalDate,
) {
    companion object {
        fun of(stage: SecretMatchingStage, quizSetId: Long?, week: OperationWeek) = SecretMatchingStateResponse(
            state = stage.state,
            nextRoundAt = stage.nextRoundAt,
            quizSetId = quizSetId,
            weekStartedOn = week.startedOn,
        )
    }
}
