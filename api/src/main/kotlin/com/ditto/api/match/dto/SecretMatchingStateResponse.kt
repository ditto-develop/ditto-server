package com.ditto.api.match.dto

import com.ditto.api.match.SecretMatchingCard
import com.ditto.api.match.SecretMatchingState
import com.ditto.domain.system.OperationWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class SecretMatchingStateResponse(
    val state: SecretMatchingState,
    val nextRoundAt: LocalDateTime?,
    /** 이번 주 1:1 퀴즈셋. 목~토에는 current-week 목록이 비어 있어 퀴즈로 바로 들어갈 때 쓴다 */
    val quizSetId: Long?,
    val weekStartedOn: LocalDate,
) {
    companion object {
        fun of(card: SecretMatchingCard, quizSetId: Long, week: OperationWeek) = SecretMatchingStateResponse(
            state = card.state,
            nextRoundAt = card.nextRoundAt,
            quizSetId = quizSetId,
            weekStartedOn = week.startedOn,
        )

        /** 플래그가 꺼져 있거나 이번 주 1:1 셋이 없을 때 */
        fun none(week: OperationWeek) = SecretMatchingStateResponse(
            state = SecretMatchingState.NONE,
            nextRoundAt = null,
            quizSetId = null,
            weekStartedOn = week.startedOn,
        )
    }
}
