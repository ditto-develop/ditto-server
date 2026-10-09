package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.domain.member.entity.Member
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import java.time.LocalDateTime

/** 1:1·그룹 공통으로, 매칭 풀에 들기 전 단계에서 빠진 이유. 풀에 들 수 있으면 null 이다. */
object PrePoolMiss {
    fun of(progress: QuizProgress, member: Member?, generatedAt: LocalDateTime?): MatchMissReason? = when {
        member == null -> MatchMissReason.MEMBER_DELETED
        progress.status != QuizProgressStatus.COMPLETED -> MatchMissReason.NOT_COMPLETED
        isCompletedAfter(progress, generatedAt) -> MatchMissReason.COMPLETED_AFTER_GENERATION
        else -> null
    }

    // 완주 뒤에는 진행 기록이 바뀌지 않아 수정 시각이 곧 완주 시각이다.
    fun isCompletedAfter(progress: QuizProgress, generatedAt: LocalDateTime?): Boolean =
        generatedAt != null && progress.updatedAt.isAfter(generatedAt)
}
