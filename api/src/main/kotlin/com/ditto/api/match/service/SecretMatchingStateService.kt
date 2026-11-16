package com.ditto.api.match.service

import com.ditto.api.match.SecretMatchingFacts
import com.ditto.api.match.SecretMatchingStage
import com.ditto.api.match.dto.SecretMatchingStateResponse
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.FeatureFlagKey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SecretMatchingStateService(
    private val featureFlags: FeatureFlags,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val sanctionRepository: SanctionRepository,
) {

    fun getSecretMatchingState(memberId: Long, now: LocalDateTime): SecretMatchingStateResponse {
        val week = OperationWeek.containing(now.toLocalDate())
        if (!featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)) {
            return SecretMatchingStateResponse.of(SecretMatchingStage.none(), null, week)
        }
        val quizSet = findActiveOneToOneQuizSet(week)
            ?: return SecretMatchingStateResponse.of(SecretMatchingStage.none(), null, week)

        val stage = SecretMatchingStage.determine(collectFacts(memberId, now, week, quizSet))
        return SecretMatchingStateResponse.of(stage, quizSet.id, week)
    }

    // 주차·유형당 활성 셋은 하나다.
    private fun findActiveOneToOneQuizSet(week: OperationWeek): QuizSet? =
        quizSetRepository.findByWeekStartedOn(week.startedOn)
            .firstOrNull { it.isActive && it.matchingType == MatchingType.ONE_TO_ONE }

    private fun collectFacts(memberId: Long, now: LocalDateTime, week: OperationWeek, quizSet: QuizSet) =
        SecretMatchingFacts(
            now = now,
            week = week,
            oneToOneQuizSet = quizSet,
            oneToOneProgress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id),
            hasOneToOneAcceptance = personalMatchRepository.findMatchByQuizSetIdAndStatusAndMemberId(
                quizSet.id,
                PersonalMatchStatus.ACCEPTED,
                memberId,
            ) != null,
            isAcceptedInFormedGroup = groupMatchMemberRepository.existsAcceptedInFormedGroupOfWeek(memberId, week.startedOn),
            hasCompletedGroupQuiz = quizSetRepository.findCompletedQuizSetInWeek(memberId, MatchingType.GROUP, week.startedOn) != null,
            isQuizBlocked = sanctionRepository.existsActiveWarningAt(memberId, now),
            hasSecretCandidates = matchCandidateRepository.existsByOwnerMemberIdAndQuizSetIdAndCandidateOriginNot(
                memberId,
                quizSet.id,
                CandidateOrigin.REGULAR,
            ),
        )
}
