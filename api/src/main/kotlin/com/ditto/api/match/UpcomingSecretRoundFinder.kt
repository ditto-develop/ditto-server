package com.ditto.api.match

import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.FeatureFlagKey
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 매칭 알림에 시크릿 매칭 안내를 붙일지 정한다. 붙일 때만 다음 라운드 시각을 돌려준다.
 * 노매칭·그룹 미성사 알림은 재시도로 지난 주 것이 늦게 나갈 수 있어서, 알림의 주가 지금 주이고
 * 라운드가 남아 있을 때만 안내한다.
 */
@Component
class UpcomingSecretRoundFinder(
    private val featureFlags: FeatureFlags,
    private val quizSetRepository: QuizSetRepository,
) {

    fun nextRoundToAnnounce(week: OperationWeek, now: LocalDateTime): LocalDateTime? {
        if (!featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)) return null
        if (week != OperationWeek.containing(now.toLocalDate())) return null
        if (!hasActiveOneToOneQuizSet(week)) return null
        return SecretMatchingSchedule.nextRoundAfter(week, now)
    }

    private fun hasActiveOneToOneQuizSet(week: OperationWeek): Boolean =
        quizSetRepository.findByWeekStartedOn(week.startedOn)
            .any { it.isActive && it.matchingType == MatchingType.ONE_TO_ONE }
}
