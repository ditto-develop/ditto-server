package com.ditto.api.match

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime

/** 홈 시크릿 매칭 카드 종류. 화면 ID 는 기획서 기준이다. */
enum class SecretMatchingState {
    /** 카드 없음 */
    NONE,

    /** SM-01 */
    ELIGIBLE,

    /** SM-02 */
    WAITING,

    /** SM-03 */
    AUTO_ENTERED,

    /** SM-06 으로 보낸다 */
    SECRET_RESULT,

    /** SM-04 */
    ENDED,
}

/** 플래그가 켜져 있고 이번 주 활성 1:1 셋이 있을 때 카드를 정하는 데 필요한 사실. 모두 [now] 기준 [week] 것이다. */
data class SecretMatchingFacts(
    val now: LocalDateTime,
    val week: OperationWeek,
    val oneToOneQuizSet: QuizSet,
    val oneToOneProgress: QuizProgress?,
    val hasAcceptedOneToOneMatch: Boolean,
    val isAcceptedInFormedGroup: Boolean,
    /** 그룹 퀴즈를 완주하지 않았으면 null */
    val groupMatchingStatus: GroupMatchingStatus?,
    val isUnderWarningSanction: Boolean,
    val hasSecretCandidates: Boolean,
)

/** 홈 시크릿 매칭 카드와 FE 가 "금/토요일" 문구를 고를 다음 라운드 시각. */
data class SecretMatchingCard(
    val state: SecretMatchingState,
    val nextRoundAt: LocalDateTime?,
) {
    companion object {
        // 조건이 겹치므로 위에서부터 빠져나가는 순서가 곧 규칙이다.
        fun determine(facts: SecretMatchingFacts): SecretMatchingCard {
            if (facts.now <= facts.oneToOneQuizSet.endDate) return none()
            if (facts.hasAcceptedOneToOneMatch || facts.isAcceptedInFormedGroup) return none()
            if (facts.isUnderWarningSanction) return none()
            if (facts.hasSecretCandidates) return facts.cardWithNextRound(SecretMatchingState.SECRET_RESULT)

            val progress = facts.oneToOneProgress
            if (progress?.status == QuizProgressStatus.COMPLETED) return completedCard(facts, progress)
            return unfinishedCard(facts)
        }

        fun none() = SecretMatchingCard(SecretMatchingState.NONE, null)

        private fun completedCard(facts: SecretMatchingFacts, progress: QuizProgress): SecretMatchingCard {
            facts.ownRoundAtIfWaiting(progress)?.let { return SecretMatchingCard(SecretMatchingState.WAITING, it) }
            if (facts.isBeforeLastRound()) return facts.cardWithNextRound(SecretMatchingState.AUTO_ENTERED)
            return ended()
        }

        private fun unfinishedCard(facts: SecretMatchingFacts): SecretMatchingCard {
            if (facts.groupMatchingStatus?.canTakeOneToOne(facts.week, facts.now) == false) return none()
            if (facts.isBeforeLastRound()) return facts.cardWithNextRound(SecretMatchingState.ELIGIBLE)
            return ended()
        }

        private fun ended() = SecretMatchingCard(SecretMatchingState.ENDED, null)

        // 늦게 완주했으면 완주 뒤 첫 라운드가 자기 라운드다. 라운드 풀의 isCompletedBefore 와 같은 기준이다.
        private fun SecretMatchingFacts.ownRoundAtIfWaiting(progress: QuizProgress): LocalDateTime? {
            if (!progress.isCompletedLate(oneToOneQuizSet.endDate)) return null
            val completedAt = progress.completedAt ?: return null
            return SecretMatchingSchedule.nextRoundAfter(week, completedAt)?.takeIf { now < it }
        }

        private fun SecretMatchingFacts.isBeforeLastRound(): Boolean =
            now < SecretMatchingSchedule.lateSubmissionClosesAt(week)

        private fun SecretMatchingFacts.cardWithNextRound(state: SecretMatchingState) =
            SecretMatchingCard(state, SecretMatchingSchedule.nextRoundAfter(week, now))
    }
}
