package com.ditto.api.match

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** 홈 시크릿 매칭 카드. FE 는 [state]로 카드를 고르고 [nextRoundAt]으로 "금/토요일" 문구를 고른다. */
enum class SecretMatchingState {
    /** 카드 없음. 플래그 꺼짐, 월~수, 이번 주 1:1 셋 없음, 이미 성사, 경고 제재 중, 그룹 응답 마감 전 그룹 완주자 */
    NONE,

    /** 1:1 퀴즈를 풀면 시크릿 매칭에 들어간다 (SM-01) */
    ELIGIBLE,

    /** 늦게 완주해 자기 라운드를 기다린다 (SM-02) */
    WAITING,

    /** 완주했고 아직 성사가 없어 다음 라운드에 자동으로 들어간다 (SM-03) */
    AUTO_ENTERED,

    /** 시크릿 매칭 후보가 있다. 일요일까지 결과 화면(SM-06)으로 보낸다 */
    SECRET_RESULT,

    /** 토 라운드까지 끝났다 (SM-04) */
    ENDED,
}

/** 플래그가 켜져 있고 이번 주 활성 1:1 셋이 있을 때 홈 상태를 정하는 데 필요한 사실. 모두 [now] 기준 [week] 것이다. */
data class SecretMatchingFacts(
    val now: LocalDateTime,
    val week: OperationWeek,
    val oneToOneQuizSet: QuizSet,
    val oneToOneProgress: QuizProgress?,
    val hasOneToOneAcceptance: Boolean,
    val isAcceptedInFormedGroup: Boolean,
    val hasCompletedGroupQuiz: Boolean,
    val isQuizBlocked: Boolean,
    val hasSecretCandidates: Boolean,
)

data class SecretMatchingStage(
    val state: SecretMatchingState,
    val nextRoundAt: LocalDateTime?,
) {
    companion object {
        // 조건이 겹치므로 위에서부터 빠져나가는 순서가 곧 규칙이다.
        fun determine(facts: SecretMatchingFacts): SecretMatchingStage {
            val quizSet = facts.oneToOneQuizSet
            if (facts.now <= quizSet.endDate) return none()
            if (facts.hasOneToOneAcceptance || facts.isAcceptedInFormedGroup || facts.isQuizBlocked) return none()
            if (facts.hasSecretCandidates) return facts.stageWithNextRound(SecretMatchingState.SECRET_RESULT)

            val progress = facts.oneToOneProgress
            if (progress?.status == QuizProgressStatus.COMPLETED) return completedStage(facts, quizSet, progress)
            return unfinishedStage(facts)
        }

        private fun completedStage(
            facts: SecretMatchingFacts,
            quizSet: QuizSet,
            progress: QuizProgress,
        ): SecretMatchingStage {
            val completedAt = progress.completedAt
            if (completedAt != null && !progress.isCompletedOnTime(quizSet.endDate)) {
                val ownRoundAt = SecretMatchingSchedule.nextRoundAfter(facts.week, completedAt)
                if (ownRoundAt != null && facts.now < ownRoundAt) {
                    return SecretMatchingStage(SecretMatchingState.WAITING, ownRoundAt)
                }
            }
            if (facts.isBeforeLastRound()) return facts.stageWithNextRound(SecretMatchingState.AUTO_ENTERED)
            return SecretMatchingStage(SecretMatchingState.ENDED, null)
        }

        // 그룹 완주자는 그룹 응답 마감 뒤에만 1:1 을 풀 수 있다. 제출 검사(QuizProgressService)와 같은 조건이다.
        private fun unfinishedStage(facts: SecretMatchingFacts): SecretMatchingStage {
            if (facts.hasCompletedGroupQuiz && !GroupResponseDeadline.hasPassed(facts.week, facts.now)) return none()
            if (facts.isBeforeLastRound()) return facts.stageWithNextRound(SecretMatchingState.ELIGIBLE)
            return SecretMatchingStage(SecretMatchingState.ENDED, null)
        }

        fun none() = SecretMatchingStage(SecretMatchingState.NONE, null)

        private fun SecretMatchingFacts.isBeforeLastRound(): Boolean =
            now < SecretMatchingSchedule.lateSubmissionClosesAt(week)

        private fun SecretMatchingFacts.stageWithNextRound(state: SecretMatchingState) =
            SecretMatchingStage(state, SecretMatchingSchedule.nextRoundAfter(week, now))
    }
}
